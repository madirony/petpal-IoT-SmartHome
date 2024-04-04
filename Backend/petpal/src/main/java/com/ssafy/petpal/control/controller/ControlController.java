package com.ssafy.petpal.control.controller;

import com.amazonaws.HttpMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.petpal.control.dto.ControlDto;
import com.ssafy.petpal.control.dto.MessageContainer;
import com.ssafy.petpal.home.service.HomeService;
import com.ssafy.petpal.image.service.ImageService;
import com.ssafy.petpal.map.dto.MapDto;
import com.ssafy.petpal.map.service.MapService;
import com.ssafy.petpal.notification.dto.NotificationRequestDto;
import com.ssafy.petpal.notification.service.FcmService;
import com.ssafy.petpal.notification.service.NotificationService;
import com.ssafy.petpal.object.dto.ApplianceResponseDto;
import com.ssafy.petpal.object.service.ApplianceService;
import com.ssafy.petpal.object.service.TargetService;
import com.ssafy.petpal.route.dto.RouteDto;
import com.ssafy.petpal.route.service.RouteService;
import com.ssafy.petpal.schedule.dto.ScheduleUpdateDto;
import com.ssafy.petpal.schedule.service.ScheduleService;
import com.ssafy.petpal.user.service.UserService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Controller
//@RequiredArgsConstructor
@AllArgsConstructor
@Slf4j
public class ControlController {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
//    private final StringRedisTemplate redisTemplate;
    private final ApplianceService applianceService;
//    private final MapService mapService;
//    private final RouteService routeService;
    private final FcmService fcmService;
    private final HomeService homeService;
//    private final UserService userService;
    private final NotificationService notificationService;
    private final ImageService imageService;
    private final TargetService targetService;
    private final ScheduleService scheduleService;
    private static final String CONTROL_QUEUE_NAME = "control.queue";
    private static final String CONTROL_EXCHANGE_NAME = "control.exchange";


    @MessageMapping("ros.control.message.{homeId}")
    public void sendMessage(@Payload String rawMessage, @DestinationVariable Long homeId) throws IOException {
//        log.info("Ros Received message: {}", rawMessage);
        ControlDto controlDto = objectMapper.readValue(rawMessage, ControlDto.class);
        String type = controlDto.getType();
        switch (type) {
            case "WEATHER": case "TURTLE": case "REGISTER": case "COMPLETE":
            rabbitTemplate.convertAndSend(CONTROL_EXCHANGE_NAME, "home." + homeId, controlDto);
            if(type.equals("COMPLETE")){
                //스캔 완료 알림 보내기
                Long targetUserId = homeService.findKakaoIdByHomeId(homeId);
                LocalDateTime nowInKorea = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                String formattedTime = nowInKorea.format(formatter);
                StringBuilder sb = new StringBuilder();
                sb.append(formattedTime);

                NotificationRequestDto notificationRequestDto
                        = new NotificationRequestDto(targetUserId, "스캔", "스캔이 완료되었습니다.",
                        formattedTime,"https://i.imgur.com/UemjaFZ.png");
                fcmService.sendMessageTo(notificationRequestDto);
                notificationService.saveNotification(notificationRequestDto); // DB에 저장
            }
            break;
            case "SCOMPLETE":
                MessageContainer.S_Complete sComplete = objectMapper.convertValue(controlDto.getMessage(), MessageContainer.S_Complete.class);
                applianceService.updateApplianceStatus(homeId,sComplete.getApplianceUUID(),sComplete.getCurrentStatus());
                ApplianceResponseDto applianceResponseDto1 = applianceService.fetchApplianceByUUID(sComplete.getApplianceUUID());

                scheduleService.updateSchedule(new ScheduleUpdateDto(applianceResponseDto1.getApplianceId(),sComplete.getScheduleId(),false));

                Long targetUserId = homeService.findKakaoIdByHomeId(homeId);
                LocalDateTime nowInKorea = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                String formattedTime = nowInKorea.format(formatter);
                ApplianceResponseDto applianceResponseDto = applianceService.fetchApplianceByUUID(sComplete.getApplianceUUID());
                log.info(">>>>>><<<<<>>>>>"+applianceResponseDto.getApplianceType());
//                String downloadURL1 = imageService.generateURL(applianceResponseDto.getApplianceType()+".png", HttpMethod.GET);
//                String downloadURL1 = imageService.generateURL("noImage", HttpMethod.GET);
//                log.info("notiDTO 생성 전");
                String downloadURL1 = "noImage";
                NotificationRequestDto notificationRequestDto1
                        = new NotificationRequestDto(targetUserId, "제어", applianceResponseDto.getRoomName()+"-"+applianceResponseDto.getApplianceType()+"의 상태를 변경하였습니다.",
                        formattedTime,downloadURL1);
                fcmService.sendMessageTo(notificationRequestDto1);
                notificationService.saveNotification(notificationRequestDto1); // DB에 저장
//                log.info("notiDTO 저장 완료");
                //COMPLETE한게 가전 On/Off를 완료한 것인지 위험물 처리 프로세스를 완료한 것인지 구분을 할 필요가 있음.
            case "ACOMPLETE":
                log.info("Ros Received message: {}", rawMessage);
                // ROS에서 입증한 실제 가전상태 데이터를 redis에 올린다.
//                controlDto.getMessage() //parsing
//                MessageContainer.A_Complete aComplete = objectMapper.readValue(controlDto.getMessage(),MessageContainer.A_Complete.class);
//                log.info("변환 전");
                MessageContainer.A_Complete aComplete = objectMapper.convertValue(controlDto.getMessage(), MessageContainer.A_Complete.class);
//                log.info("변환 후: "+aComplete.toString());
                applianceService.updateApplianceStatus(homeId,aComplete.getApplianceUUID(),aComplete.getCurrentStatus());

//                log.info("스테이터스 변환 완료");
//              fcm 호출.
                //가전 상태 제어 완료 알림 보내기!
                Long targetUserId2 = homeService.findKakaoIdByHomeId(homeId);
                LocalDateTime nowInKorea2 = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

                DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                String formattedTime2 = nowInKorea2.format(formatter2);
                ApplianceResponseDto applianceResponseDto2 = applianceService.fetchApplianceByUUID(aComplete.getApplianceUUID());
//                log.info(">>>>>><<<<<>>>>>"+applianceResponseDto2.getApplianceType());
//                String downloadURL2 = imageService.generateURL(applianceResponseDto2.getApplianceType()+".png", HttpMethod.GET);
//                String downloadURL2 = imageService.generateURL("noImage", HttpMethod.GET);
//                log.info("notiDTO 생성 전");
                String downloadURL2 = "noImage";
                NotificationRequestDto notificationRequestDto2
                        = new NotificationRequestDto(targetUserId2, "제어", applianceResponseDto2.getRoomName()+"-"+applianceResponseDto2.getApplianceType()+"의 상태를 변경하였습니다.",
                        formattedTime2,downloadURL2);
                    fcmService.sendMessageTo(notificationRequestDto2);
                notificationService.saveNotification(notificationRequestDto2); // DB에 저장
//                log.info("notiDTO 저장 완료");
            break;

            case "OCOMPLETE":
                MessageContainer.O_Complete oComplete = objectMapper.convertValue(controlDto.getMessage(), MessageContainer.O_Complete.class);

                String filename = targetService.fetchFilenameByTargetId(oComplete.getObjectId());
                String downloadURL3 = imageService.generateURL(filename, HttpMethod.GET);

                Long targetUserId3 = homeService.findKakaoIdByHomeId(homeId);
                LocalDateTime nowInKorea3 = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

                DateTimeFormatter formatter3 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                String formattedTime3 = nowInKorea3.format(formatter3);
                NotificationRequestDto notificationRequestDto3
                        = new NotificationRequestDto(targetUserId3, "처리", oComplete.getObjectType()+"를 처리하였습니다.",
                        formattedTime3,downloadURL3);
                fcmService.sendMessageTo(notificationRequestDto3);
                notificationService.saveNotification(notificationRequestDto3);
            break;
        }
    }

    @MessageMapping("control.message.{homeId}")
    public void sendMessage(@Payload ControlDto controlDto, @DestinationVariable Long homeId) throws IOException {
//        log.info("Received message: {}", rawMessage);
//        ControlDto controlDto = objectMapper.readValue(rawMessage, ControlDto.class);
        String type = controlDto.getType();
        switch (type){
            case "ON": case "OFF":
//            case "WEATHER": case "TURTLE":
            case "SCAN": case "IOT": case "MODE":
            case "REGISTER_REQUEST" : // case "REGISTER_RESPONSE":
            case "COMPLETE": //이건 스캔 완료 COMPLETE
                rabbitTemplate.convertAndSend(CONTROL_EXCHANGE_NAME, "home." + homeId, controlDto);
                break;
            default:
                break;
        }
    }

    @MessageMapping("images.stream.{homeId}.images")
    public void sendImagesData(@Payload String rawMessage, @DestinationVariable String homeId) throws JsonProcessingException {
        ControlDto controlDto = objectMapper.readValue(rawMessage, ControlDto.class);
        //type : IMAGE
        rabbitTemplate.convertAndSend(CONTROL_EXCHANGE_NAME, "home." + homeId + ".images", controlDto);
    }

    @MessageMapping("images.stream.{homeId}.yolo")
    public void sendImagesDataYolo(@Payload String rawMessage, @DestinationVariable String homeId) throws JsonProcessingException {
        ControlDto controlDto = objectMapper.readValue(rawMessage, ControlDto.class);
        //type : YOLO
        rabbitTemplate.convertAndSend(CONTROL_EXCHANGE_NAME, "home." + homeId + ".yolo", controlDto);
    }

    @MessageMapping("scan.map.{homeId}")
    public void sendMapData(@Payload String rawMessage, @DestinationVariable String homeId) throws JsonProcessingException {
        ControlDto controlDto = objectMapper.readValue(rawMessage, ControlDto.class);
        String type = controlDto.getType();
        switch (type){
//            case "SCAN":
//                rabbitTemplate.convertAndSend(CONTROL_EXCHANGE_NAME, "home." + homeId, controlDto);
//                break;
//            case "COMPLETE":
//                // 날것의 맵
//                // dtoMapper로 만들어서
//                // mapService.createMap(dto)
//                // 메세지 발행(깎은 맵이 들어가있다)
//                MapDto mapDto = mapService.createMap(homeId, controlDto.getMessage());
//                rabbitTemplate.convertAndSend(CONTROL_EXCHANGE_NAME, "home." + homeId, mapDto);
//                break;
//            case "ROUTE":
//                // 경로 저장 repository
//                RouteDto routeDto = routeService.saveRoute(homeId, controlDto.getMessage());
//                break;
        }
    }

//    @RabbitListener(queues = CONTROL_QUEUE_NAME)
//    public void receive(ControlDto controlDto) {
//
////        log.info(" log : " + controlDto);
//    }


}
