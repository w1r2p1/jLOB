import cache.Cache;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import config.Config;
import config.DatabaseConfig;
import config.RedisConfig;
import connectivity.LimitOrderBookFixServerRunner;
import connectivity.LimitOrderBookHttpServerRunner;
import connectivity.ServerRunner;
import db.Migrator;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import state.LimitOrderBook;
import state.LimitOrderBookListener;
import state.PersistenceLimitOrderBookListener;

import java.io.IOException;

public class ApplicationModule extends AbstractModule {

    private final String configPath;

    public ApplicationModule(String configPath) {
        this.configPath = configPath;
    }

    @Provides
    public Config config() throws IOException {
        return new Config(configPath);
    }

    @Provides
    public DatabaseConfig databaseConfig(Config config) {
        return config.getDatabaseConfig();
    }

    @Provides
    public DSLContext dslContext(DatabaseConfig databaseConfig) {
        return DSL.using(
                databaseConfig.getUrl(),
                databaseConfig.getUsername(),
                databaseConfig.getPassword()
        );
    }

    @Provides
    public RedisConfig redisConfig(Config config) {
        return config.getRedisConfig();
    }

    @Provides
    public Cache cache(RedisConfig redisConfig) {
        return new Cache(redisConfig);
    }

    @Provides
    public Migrator migrator(DatabaseConfig databaseConfig) {
        return new Migrator(databaseConfig);
    }

    @Provides
    public ServerRunner serverRunner(Config config, LimitOrderBook limitOrderBook) {
        return config.getProtocol().equals("http") ?
                new LimitOrderBookHttpServerRunner(limitOrderBook) :
                new LimitOrderBookFixServerRunner(limitOrderBook);
    }

    @Provides
    public LimitOrderBookListener listener(DSLContext database, Cache cache) {
        return new PersistenceLimitOrderBookListener(cache, database);
    }

    @Override
    protected void configure() {
    }
}
