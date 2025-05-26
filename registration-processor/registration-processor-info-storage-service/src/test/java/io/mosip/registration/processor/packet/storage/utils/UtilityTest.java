package io.mosip.registration.processor.packet.storage.utils;

import io.mosip.kernel.biometrics.commons.CbeffValidator;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.core.bioapi.exception.BiometricException;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.idrepo.RidDto;
import io.mosip.registration.processor.core.idrepo.dto.ResponseDTO;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.packet.manager.idreposervice.IdRepoService;
import io.mosip.registration.processor.packet.storage.exception.IdentityNotFoundException;
import io.mosip.registration.processor.packet.storage.repository.BasePacketRepository;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.kernel.core.logger.spi.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UtilityTest {
    @InjectMocks
    private Utility utility;

    @Mock
    private PriorityBasedPacketManagerService packetManagerService;

    @Mock
    private IdRepoService idRepoService;

    @Mock
    private BasePacketRepository basePacketRepository;

    @Mock
    private Logger regProcLogger;

    @Mock
    private CbeffValidator cbeffValidator;

    private InternalRegistrationStatusDto registrationStatusDto;
    private RidDto ridDto;
    private SimpleDateFormat sdf;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        // Initialize test data
        registrationStatusDto = new InternalRegistrationStatusDto();
        registrationStatusDto.setRegistrationId("10049100271000420250319064824");
        registrationStatusDto.setRegistrationType("UPDATE");
        ridDto = new RidDto();
        ridDto.setRid("10049100271000420240319064824");
        String dateTimeStr = "2024-01-01T12:00:00";
//        LocalDateTime ldt = LocalDateTime.parse(dateTimeStr);  // Parses ISO 8601 format
//        Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        ridDto.setUpd_dtimes(dateTimeStr);
        sdf = new SimpleDateFormat("yyyy/MM/dd");
        objectMapper = new ObjectMapper();

        // Set configuration values
        ReflectionTestUtils.setField(utility, "ageLimit", "5");
        ReflectionTestUtils.setField(utility, "dobFormat", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        ReflectionTestUtils.setField(utility, "expectedLifeSpan", 100);
        ReflectionTestUtils.setField(utility, "bufferInMonthes", 1);
        ReflectionTestUtils.setField(utility, "MinAgeLimit", 0);
        ReflectionTestUtils.setField(utility, "MaxAgeLimit", 150);

        // Verify that mocks are injected
        assertNotNull(packetManagerService, "packetManagerService mock should not be null");
        assertNotNull(idRepoService, "idRepoService mock should not be null");
        assertNotNull(basePacketRepository, "basePacketRepository mock should not be null");
        assertNotNull(regProcLogger, "regProcLogger mock should not be null");
    }

    @Test
    public void testWasApplicantInfant_Success_getFromIdrepo() throws Exception {
        // Setup
        String uin = "12345";
        String dob = "2023/01/01";
        String packetCreatedDate = "2025-04-30T07:04:49.681Z";
        Mockito.when(packetManagerService.getField(anyString(), anyString(), anyString(), any(ProviderStageName.class)))
                .thenReturn(uin);
        Map<String, String> response = new HashMap<>();
        response.put("dateOfBirth", dob);
        response.put("packet_created_on", packetCreatedDate);
        //response.put("packet_created_on", packetCreatedDate);
        String jsonString = new ObjectMapper().writeValueAsString(response);
        JSONObject identityJson = JsonUtil.objectMapperReadValue(jsonString, JSONObject.class);
        Mockito.when(idRepoService.getIdJsonFromIDRepo(anyString(), any())).thenReturn(identityJson);

        // Execute
        boolean result = utility.wasApplicantInfant(registrationStatusDto);

        // Verify
        assertTrue(result, "Applicant should be considered an infant (age < 5)");
    }

    @Test
    public void testWasApplicantInfant_getFromListTable() throws Exception {
        // Setup
        String uin = "12345";
        String dob = "2023/01/01";
        Map<String, String> response = new HashMap<>();
        response.put("dateOfBirth", dob);
        String jsonString = new ObjectMapper().writeValueAsString(response);
        JSONObject identityJson = JsonUtil.objectMapperReadValue(jsonString, JSONObject.class);
        when(packetManagerService.getField(anyString(), anyString(), anyString(), any(ProviderStageName.class)))
                .thenReturn(uin);
        when(idRepoService.getIdJsonFromIDRepo(anyString(), any())).thenReturn(null).thenReturn(identityJson);
        when(idRepoService.getRidByIndividualId(anyString())).thenReturn(ridDto);
        when(basePacketRepository.getPacketIdfromRegprcList(anyString())).thenReturn("10049100271000420250319064824");

        // Execute
        boolean result = utility.wasApplicantInfant(registrationStatusDto);

        // Verify
        assertTrue(result, "Applicant should be considered an infant (age < 2)");
        verify(packetManagerService, atLeastOnce()).getField(anyString(), anyString(), anyString(), any());
    }

    @Test
    public void testPacketCreatedDateTimeByRidFromIdRepo_Success() throws Exception {
        // Setup
        String uin = "12345";
        String dob = "2023/01/01";
        when(packetManagerService.getField(anyString(), anyString(), anyString(), any(ProviderStageName.class)))
                .thenReturn(uin);
        Map<String, String> response = new HashMap<>();
        response.put("dateOfBirth", dob);
        RidDto ridDto1=new RidDto();
        ridDto1.setRid("10049100271000420250319064824");
        String jsonString = new ObjectMapper().writeValueAsString(response);
        JSONObject identityJson = JsonUtil.objectMapperReadValue(jsonString, JSONObject.class);
        when(idRepoService.getIdJsonFromIDRepo(anyString(), any())).thenReturn(identityJson);
        when(idRepoService.getRidByIndividualId(anyString())).thenReturn(ridDto1);
        when(basePacketRepository.getPacketIdfromRegprcList(any())).thenReturn(null);

        // Execute
        boolean result = utility.wasApplicantInfant(registrationStatusDto);

        // Verify
        assertTrue(result);
    }

    @Test
    public void testGetPacketUpdateDateAndTimesFromIdRepo_Success() throws Exception {
        // Setup
        String uin = "12345";
        String dob = "2023/01/01";
        when(packetManagerService.getField(anyString(), anyString(), anyString(), any(ProviderStageName.class)))
                .thenReturn(uin);
        Map<String, String> response = new HashMap<>();
        response.put("dateOfBirth", dob);
        RidDto ridDto1=new RidDto();
        ridDto1.setUpd_dtimes("2025-04-30T07:04:49.681Z");
        String jsonString = new ObjectMapper().writeValueAsString(response);
        JSONObject identityJson = JsonUtil.objectMapperReadValue(jsonString, JSONObject.class);
        when(idRepoService.getIdJsonFromIDRepo(anyString(), any())).thenReturn(identityJson);
        when(idRepoService.getRidByIndividualId(anyString())).thenReturn(ridDto1);
        when(basePacketRepository.getPacketIdfromRegprcList(any())).thenReturn(null);

        // Execute
        boolean result = utility.wasApplicantInfant(registrationStatusDto);

        // Verify
        assertTrue(result);
    }

    @Test
    public void testGetDateOfBirthFromIdRepo_Success() throws Exception {
        // Setup
        String uin = "12345";
        String dob = "2023/01/01";
        when(packetManagerService.getField(anyString(), anyString(), anyString(), any(ProviderStageName.class)))
                .thenReturn(uin);
        Map<String, String> response = new HashMap<>();
        response.put("dateOfBirth", dob);
        String jsonString = new ObjectMapper().writeValueAsString(response);
        JSONObject identityJson = JsonUtil.objectMapperReadValue(jsonString, JSONObject.class);
        when(idRepoService.getIdJsonFromIDRepo(anyString(), any()))
                .thenReturn(identityJson);

        // Execute
        String result = utility.getDateOfBirthFromIdrepo(uin, registrationStatusDto.getRegistrationType());

        // Verify
        assertEquals("2023-01-01T00:00:00.000Z", result);
        verify(packetManagerService, times(1)).getField(anyString(), anyString(), anyString(), any());
    }

    @Test
    public void testParseDate_FromRidSuccess() throws Exception {
        // Setup
        String packetCreatedDate = "20250319064824";

        // Execute
        String result = utility.parseDate(packetCreatedDate);

        // Verify
        assertEquals("2025-03-19T06:48:24.000Z", result);
    }

    @Test
    public void testParseDate_FromIdRepoSuccess() throws Exception {
        // Setup
        String packetCreatedDate = "2025-03-19T06:48:24.000Z";

        // Execute
        String result = utility.parseDate(packetCreatedDate);

        // Verify
        assertEquals("2025-03-19T06:48:24.000Z", result);
    }

    @Test
    public void testIsValidDate_ValidDate() throws ParseException {
        // Setup
        Date date= Date.from(Instant.parse("2025-03-19T06:48:24.000Z"));

        // Execute
        boolean result = utility.isValidDate(date);

        // Verify
        assertTrue(result);
    }

    @Test
    public void testIsValidDate_ValidDate_failed() throws ParseException {
        // Execute
      String date  = utility.parseDate("2026-03-19T06:48:24.000Z");


//       Verify
        assertNull(date);

    }

    @Test
    public void testCalculateAgeAtTheTimeOfRegistration_Success() throws Exception {
        // Setup
        Date dob = sdf.parse("2020/01/01");
        Date registeredDate = utility.convertToDate("2024-03-19T06:48:24.000Z");



        // Execute
        int age = utility.calculateAgeAtTheTimeOfRegistration(dob, registeredDate);

        // Verify
        assertEquals(4, age);
    }

    @Test
    public void testGetResponseFromIdRepo_Success() throws Exception {
        // Setup
        String uin = "12345";
        when(packetManagerService.getField(anyString(), anyString(), anyString(), any(ProviderStageName.class)))
                .thenReturn(uin);
        when(idRepoService.getRidByIndividualId(uin)).thenReturn(ridDto);

        // Execute
        RidDto result = utility.getIndividualIdResponceFromIdrepo(registrationStatusDto.getRegistrationId(),registrationStatusDto.getRegistrationType());

        // Verify
        assertEquals(ridDto, result);
        verify(packetManagerService, times(1)).getField(anyString(), anyString(), anyString(), any());
    }

    @Test
    public void testGetPacketCreationDateTimeFromRegList_Success() throws Exception {
        // Setup
        RidDto ridDto1=new RidDto();
        ridDto1.setUpd_dtimes("2025-03-19T06:48:24.000Z");
//        ridDto1.setUpd_dtimes(utility.convertToDate("2025-03-19T06:48:24.000Z"));

        when(basePacketRepository.getPacketIdfromRegprcList(anyString())).thenReturn("10049100271000420250319064824");

        // Execute
        Date result = utility.getPacketCreationDateTimeFromRegList(ridDto.getRid());

        // Verify
        verify(basePacketRepository, times(1)).getPacketIdfromRegprcList(anyString());
    }

    @Test
    public void testWasApplicantInfant_failure() throws Exception {
        // Setup
        String uin = "12345";
        String dob = "2023/01/01";
        String packetCreatedDate = "2025-04-30T07:04:49.681Z";
        Map<String, String> response = new HashMap<>();
        response.put("dateOfBirth", dob);
        response.put("packet_created_on", packetCreatedDate);
        Mockito.when(idRepoService.getIdJsonFromIDRepo(anyString(), any())).thenReturn(null);
        RidDto ridDto1=new RidDto();
        ridDto1.setUpd_dtimes("");
        ridDto1.setRid("10049100271000420260319064824");
        when(idRepoService.getRidByIndividualId(anyString())).thenReturn(ridDto1);
        when(packetManagerService.getField(anyString(), anyString(), anyString(), any(ProviderStageName.class)))
                .thenReturn(uin);
        when(idRepoService.getIdJsonFromIDRepo(anyString(), any())).thenReturn(null).thenReturn(null);
        when(basePacketRepository.getPacketIdfromRegprcList(anyString())).thenReturn(ridDto1.getRid());

        // Execute
        IdentityNotFoundException exception = assertThrows(IdentityNotFoundException.class, () -> {
            utility.wasApplicantInfant(registrationStatusDto);
        });
    }


    @Test
    public void testGetBiometricRecordFromIdrepo() throws Exception {
        String uin="1122334455";
        String filePath= "IdrepoResponceForBiometricWithOther.json";
        ClassPathResource resource = new ClassPathResource(filePath);
        String jsonString = Files.readString(resource.getFile().toPath());
        ResponseDTO response = objectMapper.readValue(jsonString, ResponseDTO.class);
        when(idRepoService.getIdResponseFromIDRepo(anyString())).thenReturn(response);
        utility.getBiometricRecordfromIdrepo(uin);
    }

    @Test
    public void TestisALLBiometricHaveExceptionWithOthersFailure() throws JAXBException, IOException, BiometricException, PacketManagerException, ApisResourceAccessException, JsonProcessingException {
        //with other tag and some of BDB exception is marked as false (No all exception)
        String pathString= "BIRWithOther.xml";
        ClassPathResource resource1 = new ClassPathResource(pathString);
        JAXBContext jaxbContext = JAXBContext.newInstance(BIR.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        BIR bir = (BIR) unmarshaller.unmarshal(resource1.getFile());
        Boolean res=  utility.allBiometricHaveException(bir.getBirs());
        assertFalse(res);
    }

    @Test
    public void TestisALLBiometricHaveExceptionWithOutOthersFailure() throws JAXBException, IOException, BiometricException, PacketManagerException, ApisResourceAccessException, JsonProcessingException {
        String pathString= "BIRWithOutOther.xml";
        ClassPathResource resource1 = new ClassPathResource(pathString);
        JAXBContext jaxbContext = JAXBContext.newInstance(BIR.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        BIR bir = (BIR) unmarshaller.unmarshal(resource1.getFile());
        Boolean res=  utility.allBiometricHaveException(bir.getBirs());
        assertFalse(res);
    }


    @Test
    public void TestisALLBiometricHaveExceptionWithOutOthersSuccess() throws JAXBException, IOException, BiometricException, PacketManagerException, ApisResourceAccessException, JsonProcessingException {
        //with other tag and all of BDB exception is marked as true(All exception)
        String pathString= "BIRWithOtherAllExceptions.xml";
        ClassPathResource resource1 = new ClassPathResource(pathString);
        JAXBContext jaxbContext = JAXBContext.newInstance(BIR.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        BIR bir = (BIR) unmarshaller.unmarshal(resource1.getFile());
        Boolean res=  utility.allBiometricHaveException(bir.getBirs());
        assertTrue(res);
    }

    @Test
    public void TestisALLBiometricHaveExceptionWithOutOthers() throws JAXBException, IOException, BiometricException, PacketManagerException, ApisResourceAccessException, JsonProcessingException {
        String pathString= "BIRWithOutOther.xml";
        ClassPathResource resource1 = new ClassPathResource(pathString);
        JAXBContext jaxbContext = JAXBContext.newInstance(BIR.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        BIR bir = (BIR) unmarshaller.unmarshal(resource1.getFile());
        Boolean res=  utility.allBiometricHaveException(bir.getBirs());
        assertFalse(res);
    }
}