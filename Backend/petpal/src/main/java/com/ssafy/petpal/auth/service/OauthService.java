package com.ssafy.petpal.auth.service;

import com.ssafy.petpal.exception.CustomException;
import com.ssafy.petpal.exception.ErrorCode;
import com.ssafy.petpal.user.dto.UserDto;
import com.ssafy.petpal.user.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class OauthService {
    private final UserService userService;
    private final JwtTokenService jwtTokenService;
    private final KakaoOauthService kakaoOauthService;

    //카카오 로그인
    public String[] loginWithKakao(String accessToken) {
        UserDto userDto = kakaoOauthService.getUserProfileByToken(accessToken);
        return getTokens(userDto.getId());
    }

    //access Token, refresh Token 생성
    public String[] getTokens(Long id) {
        final String accessToken = jwtTokenService.createAccessToken(id.toString());
        final String refreshToken = jwtTokenService.createRefreshToken();

        UserDto userDto = userService.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_EXIST_USER));

        userDto.setRefreshToken(refreshToken);
        userService.updateRefreshToken(userDto);

        String[] arr = new String[3];
        arr[0] = accessToken; arr[1] = refreshToken; arr[2] = String.valueOf(userDto.getId());
        return arr;
    }

    // refresh Token -> access Token 새로 갱신
    public String refreshAccessToken(String refreshToken) {
        UserDto userDto = userService.findByRefreshToken(refreshToken);
        if(userDto == null) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        if(!jwtTokenService.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        return jwtTokenService.createAccessToken(userDto.getId().toString());
    }
}
