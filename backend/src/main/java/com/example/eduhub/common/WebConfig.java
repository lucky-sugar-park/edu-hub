package com.example.eduhub.common;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 설정. {@link AdminProperties}는 이 클래스에서 등록하므로 @WebMvcTest 슬라이스에서도 빈이 만들어진다. */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new CurrentEmployeeArgumentResolver());
  }
}
