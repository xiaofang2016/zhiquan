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
    private String dbTable = "sport_db_doc";
}
