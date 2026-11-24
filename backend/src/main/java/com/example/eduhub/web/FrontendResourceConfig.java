package com.example.eduhub.web;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 화면 파일을 jar 밖 폴더(eduhub.web-root, 기본 run/web)에서 서비스한다. 값이 비어 있으면 이 설정은 동작하지 않는다. 폴더 구성은 운영의 웹
 * 서버(nginx 등)와 같다: <web-root>/shell/, <web-root>/mfe/course/, <web-root>/mfe/enrollment/
 */
@Configuration
@ConditionalOnExpression("!'${eduhub.web-root:}'.isBlank()")
class FrontendResourceConfig implements WebMvcConfigurer {
  private static final CacheControl NO_CACHE = CacheControl.noCache(); // 매번 바뀌었는지 확인
  private static final CacheControl LONG =
      CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable(); // 해시 이름 파일

  private final String root; // 끝이 /인 file: 주소

  FrontendResourceConfig(@Value("${eduhub.web-root}") String webRoot) {
    // Path.toUri(): Windows의 C:\eduhub-web 도 file:///C:/eduhub-web/ 로 올바르게 바꿔 준다
    String uri = Path.of(webRoot).toAbsolutePath().normalize().toUri().toString();
    this.root = uri.endsWith("/") ? uri : uri + "/"; // 폴더가 아직 없으면 끝에 /가 붙지 않으므로
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String shell = root + "shell/";
    String mfe = root + "mfe/";
    registry
        .addResourceHandler("/index.html", "/*.js")
        .addResourceLocations(shell)
        .setCacheControl(NO_CACHE);
    registry
        .addResourceHandler("/assets/**")
        .addResourceLocations(shell + "assets/")
        .setCacheControl(LONG);
    registry
        .addResourceHandler("/mfe/*/remoteEntry.js")
        .addResourceLocations(mfe)
        .setCacheControl(NO_CACHE);
    registry.addResourceHandler("/mfe/*/assets/**").addResourceLocations(mfe).setCacheControl(LONG);
  }
}
