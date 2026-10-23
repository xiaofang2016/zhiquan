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
}
