package io.mosip.registration.processor.core.spi.packet.validator;

import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.exception.*;
import io.mosip.registration.processor.core.packet.dto.packetvalidator.PacketValidationDto;

import java.io.IOException;

public interface PacketValidator {

	boolean validate(String registrationId, String process, PacketValidationDto packetValidationDto) throws ApisResourceAccessException, RegistrationProcessorCheckedException, IOException, JsonProcessingException, PacketManagerException, PacketManagerFailureException;
}
