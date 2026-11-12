package com.example.eduhub.common;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 화면 경로를 직접 열거나 새로고침해도 프런트(`resources/static/index.html`)가 뜨게 한다.
 *
 * <p>화면 이동은 브라우저의 react-router가 처리하므로, 서버는 화면 경로 요청에 index.html만 돌려주면 된다. 경로는 프런트의
 * `apps/course-mfe/src/App.tsx`와 맞춘다. API(`/api/**`)는 여기에 넣지 않는다.
 */
@Hidden
@Controller
public class SpaForwardController {

  @GetMapping({"/admin", "/courses/{id}"})
  public String forwardToIndex() {
    return "forward:/index.html";
  }
}
