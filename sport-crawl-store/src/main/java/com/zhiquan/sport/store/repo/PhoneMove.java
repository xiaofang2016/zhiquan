package com.zhiquan.sport.store.repo;

import com.zhiquan.sport.store.aws.Attrs;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

/** 换手机号时把一条账号记录改到新号码上。验证码不搬走。 */
public final class PhoneMove {

    private PhoneMove() {
    }

    public static boolean skip(String sortKey) {
        return "SMS".equals(sortKey);
    }

    public static Map<String, AttributeValue> copy(Map<String, AttributeValue> item, String phone) {
        Map<String, AttributeValue> next = new HashMap<>(item);
        next.put("pk", Attrs.s(phone));
        if (item.containsKey("phone")) {
            next.put("phone", Attrs.s(phone));
        }
        return next;
    }
}
