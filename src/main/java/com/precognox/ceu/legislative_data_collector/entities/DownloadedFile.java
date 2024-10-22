package com.precognox.ceu.legislative_data_collector.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "downloaded_files")
public class DownloadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    private String url;
    private String contentType;
    private String filename;
    private Integer size;
    private byte[] content;

}
