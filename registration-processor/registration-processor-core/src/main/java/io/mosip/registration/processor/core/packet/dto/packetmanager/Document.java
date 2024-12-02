package io.mosip.registration.processor.core.packet.dto.packetmanager;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode
public class Document implements Serializable {

    private byte[] document;
    private String value;
    private String type;
    private String format;
    private String refNumber;
}