package com.ssafy.petpal.object.service;

import com.ssafy.petpal.home.entity.Home;
import com.ssafy.petpal.home.repository.HomeRepository;
import com.ssafy.petpal.image.entity.Image;
import com.ssafy.petpal.image.repository.ImageRepository;
import com.ssafy.petpal.object.dto.TargetRegisterDto;
import com.ssafy.petpal.object.entity.Target;
import com.ssafy.petpal.object.repository.ApplianceRepository;
import com.ssafy.petpal.object.repository.TargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TargetService {
//    private final ObjectRepository objectRepository;
    private final TargetRepository targetRepository;
    private final ApplianceRepository applianceRepository;
    private final HomeRepository homeRepository;
    private final ImageRepository imageRepository;
    public Long createTarget(TargetRegisterDto objectRegisterDto) {

        Image image = imageRepository.findById(objectRegisterDto.getImageId())
                .orElseThrow(IllegalArgumentException::new);
        Home home = homeRepository.findById(objectRegisterDto.getHomeId())
                .orElseThrow(IllegalArgumentException::new);
        Target target = Target.builder()
                .objectType(objectRegisterDto.getObjectType())
                .image(image)
                .coordinate(TargetRegisterDto.locationToPoint(objectRegisterDto.getCoordinate()))
                .home(home)
                .build();
        //TODO: Target용 Repository 생성해놓고 save()
        Target target1 = targetRepository.save(target);
        return target1.getId();
        // 알림 서비스 호출

    }

    public String fetchFilenameByTargetId(Long targetId){
        Target target = targetRepository.findById(targetId)
                .orElseThrow(IllegalArgumentException::new);
        return target.getImage().getFilename();
    }

}
