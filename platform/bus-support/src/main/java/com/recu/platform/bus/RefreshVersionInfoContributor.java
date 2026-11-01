package com.recu.platform.bus;

import java.lang.management.ManagementFactory;
import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

/** 비밀값이 아닌 갱신 버전과 프로세스 식별값만 노출한다. */
public class RefreshVersionInfoContributor implements InfoContributor {

  private final String version;
  private final String busId;

  public RefreshVersionInfoContributor(String version, String busId) {
    this.version = version;
    this.busId = busId;
  }

  @Override
  public void contribute(Info.Builder builder) {
    builder.withDetail(
        "refresh",
        Map.of(
            "version",
            version,
            "busId",
            busId,
            "pid",
            ProcessHandle.current().pid(),
            "startTime",
            ManagementFactory.getRuntimeMXBean().getStartTime()));
  }
}
