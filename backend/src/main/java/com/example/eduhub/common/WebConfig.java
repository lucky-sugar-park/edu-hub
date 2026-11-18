package com.example.eduhub.common;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 설정. 다른 빈을 주입받지 않는다(@WebMvcTest 슬라이스에서도 만들어져야 한다). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new CurrentEmployeeArgumentResolver());
  }
}
