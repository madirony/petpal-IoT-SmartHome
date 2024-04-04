package com.ssafy.petpal.map.entity;

import com.ssafy.petpal.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name ="OriginMaps")
@NoArgsConstructor
@Getter
@Setter
public class OriginMap extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "home_id")
    private Long homeId;

    @Column(name = "map_data")
    private String data;
}
