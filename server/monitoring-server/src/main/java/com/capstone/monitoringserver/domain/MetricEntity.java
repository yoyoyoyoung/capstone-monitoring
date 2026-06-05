package com.capstone.monitoringserver.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "metrics")
@Getter @Setter
@NoArgsConstructor
public class MetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String agentId;             //ID
    private Double cpuUsage;            //CPU
    private Double memoryUsage;         //RAM
    private Double diskUsage;           //DISK
    private Double netDownloadSpeed;    //Net(Download)
    private Double netUploadSpeed;      //Net(Upload)
    private Double networkLatency;      //Net(Latency)
    private Boolean isJavaAlive;        //Process(Java)
    private Boolean isMysqlAlive;       //Process(SQL)

    private LocalDateTime timestamp;
}