// package com.example.eduhub.web;
//
// import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
// import org.springframework.stereotype.Controller;
// import org.springframework.web.bind.annotation.GetMapping;
//
/// ** 첫 화면과 화면 주소(/courses/1 등)는 shell의 index.html로. /api, /assets, /mfe는 해당 없음 */
// @Controller
// @ConditionalOnExpression("!'${eduhub.web-root:}'.isBlank()")
// class SpaForwardController {
//    @GetMapping({"/", "/login", "/courses", "/courses/**", "/my/**", "/admin/**"})
//    String shell() { return "forward:/index.html"; }
// }
