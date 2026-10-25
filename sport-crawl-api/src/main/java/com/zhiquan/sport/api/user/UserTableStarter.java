package com.zhiquan.sport.api.user;

import com.zhiquan.sport.store.repo.SportUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class UserTableStarter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserTableStarter.class);

    private final SportUserRepository users;

    public UserTableStarter(SportUserRepository users) {
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            users.ensureTable();
        } catch (Exception ex) {
            log.warn("sport_user 暂时不可用: {}", ex.getMessage());
        }
    }
}
