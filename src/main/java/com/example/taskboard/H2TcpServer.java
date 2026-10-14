package com.example.taskboard;

import java.sql.SQLException;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Shares the local in-memory database with the datasync, which runs in a JVM of its own and
 * connects to {@code jdbc:h2:tcp://localhost:9092/mem:taskboard;MODE=DB2}. As a lifecycle bean the
 * server starts once the application context is refreshed, so after Flyway has migrated the schema,
 * and stops before the database is closed.
 */
@Component
@ConditionalOnBooleanProperty("taskboard.h2.tcp.enabled")
class H2TcpServer implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(H2TcpServer.class);

  private final int port;
  private Server server;

  H2TcpServer(@Value("${taskboard.h2.tcp.port}") int port) {
    this.port = port;
  }

  @Override
  public void start() {
    try {
      server = Server.createTcpServer("-tcpPort", Integer.toString(port)).start();
    } catch (SQLException e) {
      throw new IllegalStateException("Cannot start the H2 TCP server on port " + port, e);
    }
    log.info("H2 TCP server shares the database on port {}", port);
  }

  @Override
  public void stop() {
    server.stop();
  }

  @Override
  public boolean isRunning() {
    return server != null && server.isRunning(false);
  }
}
