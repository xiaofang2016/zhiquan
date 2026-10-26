package com.zhiquan.sport.store.entity;

import lombok.Data;

/**
 * 说球帝账号。编号是这里生成的，不对应 IM 用户。
 * 明文密码不放在这条资料上，仓库会另写一条记录。
 */
@Data
public class SportUser {

    private String phone;
    private String userId;
    private String nickname;
    private String avatar;
    private String passwordHash;
    private String passwordSalt;
    private String createdAt;
    private boolean jxtBound;
    private String jxtUid;
    private String jxtAccount;
    private String jxtAccessToken;
    private long jxtTokenExpire;
    private boolean uedBound;
    private String uedUid;
    private String uedAccount;
    private String uedAccessToken;
    private long uedTokenExpire;
    private long balanceCent;
}
