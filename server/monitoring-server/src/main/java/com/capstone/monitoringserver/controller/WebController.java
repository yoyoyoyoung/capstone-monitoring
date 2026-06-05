package com.capstone.monitoringserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping({"/", "/dashboard"}) // 사용자가 루트나 /dashboard로 접속하면
    public String index() {
        return "index.html"; // static 폴더 안의 index.html을 보냅니다.
    }
}