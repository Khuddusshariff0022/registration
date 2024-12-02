package io.mosip.registration.processor.core.packet.dto.packetmanager;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode
public class PacketInfo implements Serializable {

    private String id;
    private String packetName;
    private String source;
    private String process;
    private String refId;
    private String schemaVersion;
    private String signature;
    private String encryptedHash;
    private String providerName;
    private String providerVersion;
    private String creationDate;
}
