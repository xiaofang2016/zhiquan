package com.zhiquan.sport.store.aws;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "sport.aws")
public class SportAwsProperties {

    private String region = "ap-northeast-1";
    private String leagueTable = "sport_league";
    private String dateTable = "sport_match_date";
    private String matchTable = "sport_match";
    private String imageTable = "sport_image";
    private String imageBucket = "sport-crawl-images-562904761216";
    private String imagePrefix = "images/";
    /** 一场比赛一份聊天文档，key 为 chat/{matchId}.json */
    private String chatPrefix = "chat/";
    /** 资料库原文。单条可以超过 DynamoDB 400KB，正文放这里。 */
    private String dbPrefix = "database/";
    /** 社球帝帖子列表和评论。图片仍用上游地址，不把文件再存一份。 */
    private String communityPrefix = "community/";
    private String dbTable = "sport_db_doc";
    /** 说球帝自己的账号。不放 IM 用户，也不和 im 的用户表关联。 */
    private String userTable = "sport_user";
    /** 职圈抓取写进来的群和消息。调试页只读这一份。 */
    private String zhiquanChatTable = "zhiquan_chat";
    /** 当前抓取把最新群和消息写在 apk#{uid}。 */
    private String zhiquanChatPartition = "apk#5739998";
}
