package com.ssafy.petpal.auth.controller;

import com.ssafy.petpal.auth.dto.OauthRequestDto;
import com.ssafy.petpal.auth.dto.OauthResponseDto;
import com.ssafy.petpal.auth.dto.RefreshTokenResponseDto;
import com.ssafy.petpal.auth.service.OauthService;
import com.ssafy.petpal.control.dto.ControlDto;
import com.ssafy.petpal.exception.CustomException;
import com.ssafy.petpal.exception.ErrorCode;
import com.ssafy.petpal.home.dto.HomeRequestDTO;
import com.ssafy.petpal.home.entity.Home;
import com.ssafy.petpal.home.service.HomeService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class OauthController {

    private final OauthService oauthService;
    private final HomeService homeService;

    private final RabbitTemplate rabbitTemplate;
    private static final String CONTROL_QUEUE_NAME = "control.queue";
    private static final String CONTROL_EXCHANGE_NAME = "control.exchange";

    @PostMapping("/login/oauth/{provider}")
    public OauthResponseDto login(@PathVariable String provider, @RequestBody OauthRequestDto oauthRequestDto,
                                  HttpServletResponse response) {
//        log.error("[ExceptionHandlerFilter] errMsg : " + oauthRequestDto.getAccessToken());
        OauthResponseDto oauthResponseDto = new OauthResponseDto();
        switch (provider) {
            case "kakao":
                String[] arr = oauthService.loginWithKakao(oauthRequestDto.getAccessToken());

                oauthResponseDto.setAccessToken(arr[0]);
                oauthResponseDto.setRefreshToken(arr[1]);
                List<Home> list = homeService.fetchAllByUserId(Long.valueOf(arr[2]));
                if(list.isEmpty())
                    oauthResponseDto.setHomeId(homeService.createHome(new HomeRequestDTO(Long.valueOf(arr[2]))));
                else
                    oauthResponseDto.setHomeId(list.get(0).getId());
//                oauthResponseDto.setHomeId(2);
        }
        ControlDto controlDto = new ControlDto();
        controlDto.setType("HOMEID");
        controlDto.setMessage(String.valueOf(oauthResponseDto.getHomeId()));
        rabbitTemplate.convertAndSend(CONTROL_EXCHANGE_NAME, "home." + oauthResponseDto.getHomeId(), controlDto);
        return oauthResponseDto;
    }

    // refresh Token -> access Token 재발급
    @PostMapping("/token/refresh")
    public RefreshTokenResponseDto tokenRefresh(HttpServletRequest request) {
        RefreshTokenResponseDto refreshTokenResponseDto = new RefreshTokenResponseDto();
        Cookie[] list = request.getCookies();
        if(list == null) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Cookie refreshTokenCookie = Arrays.stream(list).filter(cookie -> cookie.getName().equals("refresh_token")).toList().get(0);

        if(refreshTokenCookie == null) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        String accessToken = oauthService.refreshAccessToken(refreshTokenCookie.getValue());
        refreshTokenResponseDto.setAccessToken(accessToken);
        return refreshTokenResponseDto;
    }
}
