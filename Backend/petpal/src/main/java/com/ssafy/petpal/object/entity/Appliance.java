package com.ssafy.petpal.object.entity;

import com.ssafy.petpal.common.BaseEntity;
import com.ssafy.petpal.home.entity.Home;
import com.ssafy.petpal.room.entity.Room;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "Appliances")
public class Appliance extends BaseEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appliance_id")
    private Long id;

    @Column(name = "appliance_type")
    private String applianceType;

    @Column(name = "appliance_uuid")
    private String applianceUUID;

    @Column(name = "coordinate")
    Point coordinate;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;


    @ManyToOne
    @JoinColumn(name = "home_id")
    private Home home;

    @Builder
    public Appliance(String applianceType, String applianceUUID, Point coordinate, Room room, Home home){
        this.applianceType = applianceType;
        this.applianceUUID = applianceUUID;
        this.coordinate = coordinate;
        this.home = home;
        this.room = room;
    }


}
