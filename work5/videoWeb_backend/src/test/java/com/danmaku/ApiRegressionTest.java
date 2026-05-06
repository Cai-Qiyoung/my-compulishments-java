package com.danmaku;

import com.danmaku.support.InMemoryRedisTestConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemoryRedisTestConfig.class)
class ApiRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void userInfoShouldStillRejectAnonymousAccess() throws Exception {
        mockMvc.perform(get("/user/info"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mainApiFlowShouldPassAfterMybatisRefactor() throws Exception {
        String user1Name = "regression_user_1";
        String user2Name = "regression_user_2";
        String adminName = "regression_admin";
        String password = "Pass123456";

        assertSuccess(post("/user/register")
                .param("username", user1Name)
                .param("password", password));

        assertSuccess(post("/user/register")
                .param("username", user2Name)
                .param("password", password));
        assertSuccess(post("/user/register")
                .param("username", adminName)
                .param("password", password));
        jdbcTemplate.update("UPDATE `user` SET role = 'ADMIN' WHERE username = ?", adminName);

        JsonNode user1Login = assertSuccess(post("/user/login")
                .param("username", user1Name)
                .param("password", password));
        JsonNode user2Login = assertSuccess(post("/user/login")
                .param("username", user2Name)
                .param("password", password));
        JsonNode adminLogin = assertSuccess(post("/user/login")
                .param("username", adminName)
                .param("password", password));

        String user1Id = user1Login.path("data").path("id").asText();
        String user2Id = user2Login.path("data").path("id").asText();
        String user1AccessToken = user1Login.path("data").path("access_token").asText();
        String user2AccessToken = user2Login.path("data").path("access_token").asText();
        String adminAccessToken = adminLogin.path("data").path("access_token").asText();

        JsonNode user1Info = assertSuccess(get("/user/info")
                .header("Access-Token", user1AccessToken));
        assertThat(user1Info.path("data").path("username").asText()).isEqualTo(user1Name);

        MockMultipartFile avatar = new MockMultipartFile(
                "data",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                "avatar-bytes".getBytes()
        );
        JsonNode avatarUpload = assertSuccess(multipart(HttpMethod.PUT, "/user/avatar/upload")
                .file(avatar)
                .header("Access-Token", user1AccessToken));
        assertThat(avatarUpload.path("data").path("avatar_url").asText()).contains("/upload/avatar/");

        MockMultipartFile videoFile = new MockMultipartFile(
                "videoFile",
                "demo.mp4",
                "video/mp4",
                "fake-video-content".getBytes()
        );
        MockMultipartFile coverFile = new MockMultipartFile(
                "coverFile",
                "cover.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-cover".getBytes()
        );
        assertSuccess(multipart("/video/publish")
                .file(videoFile)
                .file(coverFile)
                .param("title", "回归测试视频")
                .param("description", "用于验证 MyBatis XML 接口链路")
                .header("Access-Token", user1AccessToken));

        JsonNode ownerVideoList = assertSuccess(get("/video/list")
                .param("user_id", user1Id)
                .param("page_num", "1")
                .param("page_size", "10")
                .header("Access-Token", user1AccessToken));
        assertThat(ownerVideoList.path("data").path("items")).hasSize(1);
        String videoId = ownerVideoList.path("data").path("items").get(0).path("id").asText();
        assertThat(ownerVideoList.path("data").path("items").get(0).path("audit_status").asText()).isEqualTo("PENDING");

        JsonNode pendingVideos = assertSuccess(get("/video/audit/pending")
                .param("page_num", "1")
                .param("page_size", "10")
                .header("Access-Token", adminAccessToken));
        assertThat(pendingVideos.path("data").path("items")).isNotEmpty();

        assertSuccess(post("/video/audit/review")
                .header("Access-Token", adminAccessToken)
                .param("video_id", videoId)
                .param("audit_status", "APPROVED")
                .param("audit_reason", "ok"));

        JsonNode popular = assertSuccess(get("/video/popular")
                .param("page_num", "1")
                .param("page_size", "10"));
        assertThat(popular.path("data").path("items")).isNotEmpty();

        JsonNode search = assertSuccess(post("/video/search")
                .param("keywords", "回归测试")
                .param("page_num", "1")
                .param("page_size", "10"));
        assertThat(search.path("data").path("items")).isNotEmpty();

        JsonNode detail = assertSuccess(get("/video/detail")
                .param("video_id", videoId)
                .header("Access-Token", user2AccessToken));
        assertThat(detail.path("data").path("title").asText()).isEqualTo("回归测试视频");

        assertSuccess(post("/comment/publish")
                .header("Access-Token", user2AccessToken)
                .param("video_id", videoId)
                .param("content", "这是一条回归评论")
                .param("parent_id", "0"));

        JsonNode commentList = assertSuccess(get("/comment/list")
                .param("video_id", videoId)
                .param("page_num", "1")
                .param("page_size", "10"));
        assertThat(commentList.path("data").path("records")).hasSize(1);
        String commentId = commentList.path("data").path("records").get(0).path("id").asText();

        assertSuccess(post("/like/video")
                .header("Access-Token", user2AccessToken)
                .param("video_id", videoId));

        assertSuccess(post("/like/comment")
                .header("Access-Token", user1AccessToken)
                .param("comment_id", commentId));

        JsonNode likeList = assertSuccess(get("/like/list")
                .param("user_id", user2Id)
                .param("page_num", "1")
                .param("page_size", "10")
                .header("Access-Token", user2AccessToken));
        assertThat(likeList.path("data").path("items")).isNotEmpty();

        assertSuccess(post("/relation/action")
                .header("Access-Token", user2AccessToken)
                .param("to_user_id", user1Id));
        assertSuccess(post("/relation/action")
                .header("Access-Token", user1AccessToken)
                .param("to_user_id", user2Id));

        JsonNode followingList = assertSuccess(get("/relation/following/list")
                .param("user_id", user2Id)
                .param("page_num", "1")
                .param("page_size", "10"));
        assertThat(followingList.path("data").path("items")).isNotEmpty();

        JsonNode fansList = assertSuccess(get("/relation/fans/list")
                .param("user_id", user1Id)
                .param("page_num", "1")
                .param("page_size", "10"));
        assertThat(fansList.path("data").path("items")).isNotEmpty();

        JsonNode friendsList = assertSuccess(get("/relation/friends/list")
                .param("page_num", "1")
                .param("page_size", "10")
                .header("Access-Token", user1AccessToken));
        assertThat(friendsList.path("data").path("items")).isNotEmpty();

        JsonNode contactList = assertSuccess(get("/contact/list")
                .param("page_num", "1")
                .param("page_size", "10")
                .header("Access-Token", user1AccessToken));
        assertThat(contactList.path("data").path("items")).isNotEmpty();

        JsonNode sessionCreate = assertSuccess(post("/session/single")
                .header("Access-Token", user1AccessToken)
                .param("target_user_id", user2Id));
        String conversationId = sessionCreate.path("data").path("conversation_id").asText();

        JsonNode sessionList = assertSuccess(get("/session/list")
                .param("page_num", "1")
                .param("page_size", "10")
                .header("Access-Token", user1AccessToken));
        assertThat(sessionList.path("data").path("items")).isNotEmpty();

        assertSuccess(post("/message/send")
                .header("Access-Token", user1AccessToken)
                .param("conversation_id", conversationId)
                .param("message_type", "TEXT")
                .param("content", "hello"));

        JsonNode messageHistory = assertSuccess(get("/message/history")
                .header("Access-Token", user1AccessToken)
                .param("conversation_id", conversationId)
                .param("page_num", "1")
                .param("page_size", "10"));
        assertThat(messageHistory.path("data").path("items")).isNotEmpty();

        String longMessage = IntStream.range(0, 620)
                .mapToObj(index -> String.valueOf((char) ('a' + (index % 26))))
                .collect(Collectors.joining());
        JsonNode longMessageSend = assertSuccess(post("/message/send")
                .header("Access-Token", user1AccessToken)
                .param("conversation_id", conversationId)
                .param("message_type", "TEXT")
                .param("content", longMessage));
        assertThat(longMessageSend.path("data").path("content").asText()).isEqualTo(longMessage);

        String lastMessagePreview = jdbcTemplate.queryForObject(
                "SELECT last_message FROM `conversation` WHERE id = ?",
                String.class,
                conversationId
        );
        assertThat(lastMessagePreview).isNotNull();
        assertThat(lastMessagePreview.length()).isLessThanOrEqualTo(500);

        JsonNode historyAfterLongMessage = assertSuccess(get("/message/history")
                .header("Access-Token", user1AccessToken)
                .param("conversation_id", conversationId)
                .param("page_num", "1")
                .param("page_size", "20"));
        assertThat(historyAfterLongMessage.path("data").path("items").toString()).contains(longMessage);

        assertSuccess(delete("/comment/delete")
                .header("Access-Token", user2AccessToken)
                .param("comment_id", commentId));

        JsonNode commentListAfterDelete = assertSuccess(get("/comment/list")
                .param("video_id", videoId)
                .param("page_num", "1")
                .param("page_size", "10"));
        assertThat(commentListAfterDelete.path("data").path("records")).isEmpty();
    }

    @Test
    void strangerSingleChatShouldAllowOnlyOneMessageBeforeReply() throws Exception {
        String user1Name = "stranger_rule_user_1";
        String user2Name = "stranger_rule_user_2";
        String password = "Pass123456";

        assertSuccess(post("/user/register")
                .param("username", user1Name)
                .param("password", password));
        assertSuccess(post("/user/register")
                .param("username", user2Name)
                .param("password", password));

        JsonNode user1Login = assertSuccess(post("/user/login")
                .param("username", user1Name)
                .param("password", password));
        JsonNode user2Login = assertSuccess(post("/user/login")
                .param("username", user2Name)
                .param("password", password));

        String user1AccessToken = user1Login.path("data").path("access_token").asText();
        String user2AccessToken = user2Login.path("data").path("access_token").asText();
        String user2Id = user2Login.path("data").path("id").asText();

        JsonNode sessionCreate = assertSuccess(post("/session/single")
                .header("Access-Token", user1AccessToken)
                .param("target_user_id", user2Id));
        String conversationId = sessionCreate.path("data").path("conversation_id").asText();

        assertSuccess(post("/message/send")
                .header("Access-Token", user1AccessToken)
                .param("conversation_id", conversationId)
                .param("message_type", "TEXT")
                .param("content", "first stranger hello"));

        JsonNode blockedSecondSend = assertBusinessFail(post("/message/send")
                .header("Access-Token", user1AccessToken)
                .param("conversation_id", conversationId)
                .param("message_type", "TEXT")
                .param("content", "second stranger hello"));
        assertThat(blockedSecondSend.path("msg").asText()).contains("仅可发送一条消息");

        assertSuccess(post("/message/send")
                .header("Access-Token", user2AccessToken)
                .param("conversation_id", conversationId)
                .param("message_type", "TEXT")
                .param("content", "reply stranger hello"));

        assertSuccess(post("/message/send")
                .header("Access-Token", user1AccessToken)
                .param("conversation_id", conversationId)
                .param("message_type", "TEXT")
                .param("content", "third stranger hello after reply"));

        JsonNode blockedFourthSend = assertBusinessFail(post("/message/send")
                .header("Access-Token", user1AccessToken)
                .param("conversation_id", conversationId)
                .param("message_type", "TEXT")
                .param("content", "fourth stranger hello before next reply"));
        assertThat(blockedFourthSend.path("msg").asText()).contains("仅可发送一条消息");
    }

    private JsonNode assertSuccess(org.springframework.test.web.servlet.RequestBuilder requestBuilder) throws Exception {
        MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asInt()).isEqualTo(10000);
        return json;
    }

    private JsonNode assertBusinessFail(org.springframework.test.web.servlet.RequestBuilder requestBuilder) throws Exception {
        MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(json.path("code").asInt()).isNotEqualTo(10000);
        return json;
    }
}
