package com.zhiquan.sport.db;

import com.zhiquan.sport.store.repo.DatabaseDocRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DbStartup implements ApplicationRunner {

    private final DbProperties properties;
    private final DatabaseDocRepository docs;
    private final DatabaseCrawlService crawl;

    public DbStartup(DbProperties properties, DatabaseDocRepository docs, DatabaseCrawlService crawl) {
        this.properties = properties;
        this.docs = docs;
        this.crawl = crawl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        docs.ensureTable();
        crawl.open();
        if (!crawl.homeReady()) {
            Thread thread = new Thread(crawl::daily, "db-first");
            thread.setDaemon(true);
            thread.start();
        }
    }
}
