package io.mosip.registration.processor.packet.storage.utils;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.kernel.biometrics.commons.CbeffValidator;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.bioapi.exception.BiometricException;
import io.mosip.registration.processor.core.idrepo.RidDto;
import io.mosip.registration.processor.core.idrepo.dto.ResponseDTO;
import io.mosip.registration.processor.packet.manager.idreposervice.IdRepoService;
import io.mosip.registration.processor.packet.storage.exception.IdentityNotFoundException;
import io.mosip.registration.processor.packet.storage.repository.BasePacketRepository;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import lombok.Data;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.exception.RegistrationProcessorCheckedException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.packet.storage.exception.ParsingException;


/**
 * The Class Utility.
 *
 * @author Sowmya Banakar
 */

@Component
@Data
public class Utility {

	private static Logger regProcLogger = RegProcessorLogger.getLogger(Utility.class);
    public static final String EXCEPTION = "EXCEPTION";
    public static final String TRUE = "TRUE";
    public static final String DATEOFBIRTH="dateOfBirth";
    public static final String PACKETCREATEDDATE="packet_created_on";
    public static final String IDREPODATEFORMAT= "yyyy/MM/dd";

    /** The Constant UIN. */
    private static final String UIN = "UIN";

	@Autowired
	private PriorityBasedPacketManagerService packetManagerService;

	@Autowired
	private Utilities utilities;

    @Autowired
    private IdRepoService idRepoService;

    @Autowired
    private BasePacketRepository basePacketRepository;

    @Autowired
    private ObjectMapper objectMapper;



	/** The dob format. */
	@Value("${registration.processor.applicant.dob.format}")
	private String dobFormat;

    @Value("${mosip.bio-deduped.max_age_limit:100}")
    private int MaxAgeLimit;

    @Value("${mosip.bio-deduped.min_age_limit:0}")
    private int MinAgeLimit;

    @Value("${registration.processor.identityjson}")
    private String getRegProcessorIdentityJson;


    /** The get reg processor demographic identity. */
    @Value("${registration.processor.demographic.identity}")
    private String getRegProcessorDemographicIdentity;

    @Value("${mosip.kernel.applicant.type.age.limit}")
    private String ageLimit;

    @Value("${registration.processor.expected-life-span}")
    private int expectedLifeSpan;

    @Value("${registration.processor.packetProcessing.buffer-in-months}")
    private int bufferInMonthes;


	private static final String VALUE = "value";

	/**
	 * get applicant age by registration id. Checks the id json if dob or age
	 * present, if yes returns age if both dob or age are not present then retrieves
	 * age from id repo
	 *
	 * @param id the registration id
	 * @return the applicant age
	 * @throws IOException                           Signals that an I/O exception
	 *                                               has occurred.
	 * @throws IOException                           Signals that an I/O exception
	 *                                               has occurred.
	 * @throws ApisResourceAccessException           the packet decryption failure
	 *                                               exception
	 * @throws RegistrationProcessorCheckedException
	 */
	public int getApplicantAge(String id, String process, ProviderStageName stageName)
			throws IOException, ApisResourceAccessException, JsonProcessingException, PacketManagerException {
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), id,
				"Utility::getApplicantAge()::entry");

		String applicantDob = packetManagerService.getFieldByMappingJsonKey(id, MappingJsonConstants.DOB, process,
				stageName);
		String applicantAge = packetManagerService.getFieldByMappingJsonKey(id, MappingJsonConstants.AGE, process,
				stageName);
		if (applicantDob != null) {
			return calculateAge(applicantDob);
		} else if (applicantAge != null) {
			regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), id,
					"Utility::getApplicantAge()::exit when applicantAge is not null");
			return Integer.valueOf(applicantAge);
		} else {
			String uin = getUIn(id, process, stageName);
			JSONObject identityJSONOject = utilities.retrieveIdrepoJson(uin);
			JSONObject regProcessorIdentityJson = utilities
					.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY);
			String ageKey = JsonUtil
					.getJSONValue(JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.AGE), VALUE);
			String dobKey = JsonUtil
					.getJSONValue(JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.DOB), VALUE);
			String idRepoApplicantDob = JsonUtil.getJSONValue(identityJSONOject, dobKey);
			if (idRepoApplicantDob != null) {
				regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), id,
						"Utility::getApplicantAge()::exit when ID REPO applicantDob is not null");
				return calculateAge(idRepoApplicantDob);
			}
			Integer idRepoApplicantAge = JsonUtil.getJSONValue(identityJSONOject, ageKey);
			regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.USERID.toString(), id,
					"Utility::getApplicantAge()::exit when ID REPO applicantAge is not null");
			return idRepoApplicantAge != null ? idRepoApplicantAge : -1;

		}

	}

	/**
	 * Calculate age.
	 *
	 * @param applicantDob the applicant dob
	 * @return the int
	 */
	private int calculateAge(String applicantDob) {
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
				"Utility::calculateAge():: entry");

		DateFormat sdf = new SimpleDateFormat(dobFormat);
		Date birthDate = null;
		try {
			birthDate = sdf.parse(applicantDob);

		} catch (ParseException e) {
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					"", "Utility::calculateAge():: error with error message "
							+ PlatformErrorMessages.RPR_SYS_PARSING_DATE_EXCEPTION.getMessage());
			throw new ParsingException(PlatformErrorMessages.RPR_SYS_PARSING_DATE_EXCEPTION.getCode(), e);
		}
		LocalDate ld = new java.sql.Date(birthDate.getTime()).toLocalDate();
		Period p = Period.between(ld, LocalDate.now());
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
				"Utility::calculateAge():: exit");

		return p.getYears();

	}

	/**
	 * Get UIN from identity json (used only for update/res update/activate/de
	 * activate packets).
	 *
	 * @param id the registration id
	 * @return the u in
	 * @throws IOException                           Signals that an I/O exception
	 *                                               has occurred.
	 * @throws IOException                           Signals that an I/O exception
	 *                                               has occurred.
	 * @throws ApisResourceAccessException           the apis resource access
	 *                                               exception
	 * @throws RegistrationProcessorCheckedException
	 */
	public String getUIn(String id, String process, ProviderStageName stageName)
			throws IOException, ApisResourceAccessException, PacketManagerException, JsonProcessingException {
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), id,
				"Utility::getUIn()::entry");
		String UIN = packetManagerService.getFieldByMappingJsonKey(id, MappingJsonConstants.UIN, process, stageName);
		regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), id,
				"Utility::getUIn()::exit");

		return UIN;

	}

    // Infant Age limit taken from config.
    public boolean wasApplicantInfant(InternalRegistrationStatusDto registrationStatusDto) throws Exception {
        //Fetching the packet created date and time
        Date packetCeatedDate=getPacketcreatedDateAndtimesFromIdrepo(registrationStatusDto.getRegistrationId(), registrationStatusDto.getRegistrationType());
        if (packetCeatedDate==null){
            RidDto ridDto=new RidDto();
            //Getting the Last Interacted Rid From Idrepo.
            ridDto= getIndividualIdResponceFromIdrepo(registrationStatusDto.getRegistrationId(),registrationStatusDto.getRegistrationType());
            packetCeatedDate=getPacketCreationDateTimeFromRegList(ridDto.getRid());
            if (packetCeatedDate==null) {
                packetCeatedDate=getPacketCreatedDateTimeFromRid(ridDto.getRid());
                if (packetCeatedDate==null){
                    packetCeatedDate= getPacketUpdateDateFromIdRepo(ridDto);
                    if(packetCeatedDate==null) {
                        regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.UIN.toString(), "",
                                "Unable to get Packet Created Date and Time");
                        throw new IdentityNotFoundException(PlatformErrorMessages.RPR_BDD_PACKET_CREATED_DATE_NULL.getMessage()+PlatformErrorMessages.RPR_BDD_PACKET_CREATED_DATE_NULL.getCode());
                    }
                }
            }
        }
        Date dobOfApplicant=convertToDate(getDateOfBirthFromIdrepo(registrationStatusDto.getRegistrationId(), registrationStatusDto.getRegistrationType()));
        int age=calculateAgeAtTheTimeOfRegistration(dobOfApplicant, packetCeatedDate);
        int ageThreshold = Integer.parseInt(ageLimit);
        if (!(age < ageThreshold && age > 0)){
            regProcLogger.error("Invalid Age : {}", age);
            throw new RegistrationProcessorCheckedException(PlatformErrorMessages.RPR_BDD_INVALID_AGE);
        }
        return true;
    }


    /**    get packet created date and time from idrepo */
    public Date getPacketcreatedDateAndtimesFromIdrepo(String rid, String process) throws PacketManagerException, ApisResourceAccessException, IOException, JsonProcessingException, ParseException {
        regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.UIN.toString(), "",
                "utility::getPacketcreatedDateAndtimesFromIdrepo()::entry");
        //Getting Uin from packetmanager from update packet */
        String uin=packetManagerService.getField(rid,UIN,process,ProviderStageName.BIO_DEDUPE);
        //Get created date and time from idrepo using above UIN */
        String packetCreatedDate="";
        regProcLogger.debug("Uin = ",uin);
        JSONObject responseDTO= idRepoService.getIdJsonFromIDRepo(uin,getGetRegProcessorDemographicIdentity());
        if (responseDTO != null) {
             packetCreatedDate=JsonUtil.getJSONValue(responseDTO,PACKETCREATEDDATE);
             if (packetCreatedDate==null || packetCreatedDate=="")
             {
                 return null;
             }
        }
        else {return null;}
        String[] str=packetCreatedDate.split("T");
//        return str[0].replace("-","/");
        return convertToDate(parseDate(packetCreatedDate));
    }


    public String  getDateOfBirthFromIdrepo(String rid, String type) throws IOException, ApisResourceAccessException, PacketManagerException, JsonProcessingException, ParseException {
        regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.UIN.toString(), "",
                "utility::getDateOfDirthFromIdrepo()::entry");
        String uin=packetManagerService.getField(rid,MappingJsonConstants.UIN,type,ProviderStageName.BIO_DEDUPE);
        JSONObject responseDTO= idRepoService.getIdJsonFromIDRepo(uin,getGetRegProcessorDemographicIdentity());
        if (responseDTO != null) {
            return dateOfBirthFormatter(JsonUtil.getJSONValue(responseDTO,DATEOFBIRTH));
        }
        return null;
    }

    public String parseDate(String dateStr) {
        try {
            if (dateStr != null && !dateStr.isEmpty()) {
                // Define the target format: ISO 8601 with UTC timezone
                SimpleDateFormat outputFormat = new SimpleDateFormat(dobFormat);
                outputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

                Date date= new Date();

                if (dateStr.matches("\\d{14}")) {
                    // Handle format like "20250319064824"
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss");
                    inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    date = inputFormat.parse(dateStr);
                } else if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}T.*Z")) {
                    // Handle Standard Date format
                    Instant instant = Instant.parse(dateStr);
                    date = Date.from(instant);
                } else {
                    throw new IllegalArgumentException("Unsupported date format: " + dateStr);
                }

                if(!isValidDate(date))
                    return null;

                return outputFormat.format(date);
            }
        } catch (Exception e) {
            regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.UIN.toString(), "",
                    "e.getMessage() ");
            throw new ParsingException(e.getMessage() , e);
        }
        return null;
    }

    public String getDateFromatedString(String dt) throws ParseException {
        DateFormat sdf = new SimpleDateFormat(dobFormat);
        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        Date date = inputFormat.parse(dt);
        return sdf.format(date);
    }

    public Date convertToDate(String dateStr) throws ParseException {
        if (dateStr == null)
            return null;
        DateFormat sdf = new SimpleDateFormat(dobFormat);
        Date date = sdf.parse(dateStr);
        return date;
    }

    //Date check. "last configurable years">Date<now
    public boolean isValidDate(Date inputDate) throws ParseException {
        if (inputDate == null) {
            return false;
        }
//        Date inputDate= convertToDate(input);
        Date currentDate = new Date();
        if (inputDate.after(currentDate)) {
            regProcLogger.error("Future Date : {}",inputDate);
            return false;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);
        calendar.add(Calendar.YEAR, - expectedLifeSpan);
        Date hundredYearsAgo = calendar.getTime();
        if (inputDate.before(hundredYearsAgo)) {
            regProcLogger.error("Date is older the life Expectancy : {} , date : {}",expectedLifeSpan,inputDate);
            return false;
        }
        return true;
    }


    //Minimum and Maximum age needs to be fetched from Properties
    public int calculateAgeAtTheTimeOfRegistration(Date dob, Date registeredDate) throws Exception {
        regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
                "utility::calculateAgeAtTheTimeOfRegistration():: entry");

        // Convert Date objects to LocalDate
        LocalDate dobLocalDate = dob.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate registeredLocalDate = registeredDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        // Calculate the period between the two dates
        Period period = Period.between(dobLocalDate, registeredLocalDate);

        // Extract years from the period
        int ageInYears = period.getYears();

        // Validate age against min and max limits
        if (ageInYears < MinAgeLimit || ageInYears > MaxAgeLimit) {
            throw new IOException(PlatformErrorMessages.RPR_PDS_AGE_INVALID_EXCEPTION.getMessage());
        }

        regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
                "utility::calculateAgeAtTheTimeOfRegistration():: exit");

        // Return age in years (as per the original method signature)
        return ageInYears;
    }

    //Getting the last processed Rid from Idrepo
    public RidDto getIndividualIdResponceFromIdrepo(String rid, String process) throws IOException, ApisResourceAccessException, PacketManagerException, JsonProcessingException {
        //getting Uin from packetmanager from update packet */
        String uin=packetManagerService.getField(rid,UIN,process,ProviderStageName.BIO_DEDUPE);
        //getting Last processed Rid from Idrepo */
        RidDto ridDto=idRepoService.getRidByIndividualId(uin);
        return ridDto;
    }

    public Date getPacketCreationDateTimeFromRegList(String rid) throws PacketManagerException, ApisResourceAccessException, IOException, JsonProcessingException, ParseException {
        regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
                "utility::getPacketCreationDateTimeFromRegList():: entry");
        Date date=new Date();
        String packetId=basePacketRepository.getPacketIdfromRegprcList(rid);
        //need to check. (length of the dateAndTime)org.springframework.beans.factory.annotation.Autowired
        if(packetId!=null){
            date= convertToDate(parseDate(packetId.substring(Math.max(0, packetId.length() - 14))));
            regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
                    "utility::getPacketCreationDateTimeFromRegList():: exit");
            return date;
        }
        return null;
    }

    public Date getPacketCreatedDateTimeFromRid(String rid) throws ParseException {
        if (rid != null) {
            return convertToDate(parseDate(rid.substring(Math.max(0, rid.length() - 14))));
        }
        return null;
    }

    //if packetId does not exist in db then taking update date from idRepo and add buffer delay to it.
    public Date getPacketUpdateDateFromIdRepo(RidDto ridDto) throws ParseException {
        return convertToDate(parseDate(String.valueOf(ridDto.getUpd_dtimes())));
    }

    public BiometricRecord getBiometricRecordfromIdrepo(String uin) throws Exception {
        regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
                "utility::getBiometricRecordfromIdrepo():: entry");
        ResponseDTO responseFromIDRepo =idRepoService.getIdResponseFromIDRepo(uin);
        String doc = responseFromIDRepo.getDocuments().get(0).getValue();
        byte[] bi=Base64.getUrlDecoder().decode(doc);
        if (bi == null)
            return null;
        BIR birs = CbeffValidator.getBIRFromXML(bi);
        BiometricRecord biometricRecord = new BiometricRecord();
        biometricRecord.setSegments(birs.getBirs());
        regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
                "utility::getBiometricRecordfromIdrepo():: exit");
        return biometricRecord;
    }


    public boolean allBiometricHaveException(List<BIR> birs) throws PacketManagerException, IOException, ApisResourceAccessException, JsonProcessingException , BiometricException {
        regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
                "utility::isALLBiometricHaveExceptoin():: entry");
        if (birs == null) {
            throw new BiometricException(PlatformErrorMessages.UNABLE_TO_FETCH_BIO_INFO.getCode(), PlatformErrorMessages.UNABLE_TO_FETCH_BIO_INFO.getMessage());
        }
        if (isBiometricHavingOthers(birs)) {
            // get individual biometrics file name from id.json
            for (BIR bir : birs) {
                String st =bir.getBdbInfo().getType().get(0).toString();
                if (!(bir.getBdbInfo().getType().get(0) == BiometricType.FACE || bir.getBdbInfo().getType().get(0) == BiometricType.EXCEPTION_PHOTO)) {
                    if(bir.getOthers().get(EXCEPTION).equals(false)){
                        return false;
                    }
                }
            }
        }else {
            for (BIR bir:birs)
            {
                if(!(bir.getBdbInfo().getType().get(0) == BiometricType.FACE || bir.getBdbInfo().getType().get(0) == BiometricType.EXCEPTION_PHOTO))
                {
                    return false;
                }
            }
        }
        regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), "",
                "utility::isALLBiometricHaveExceptoin():: exit");
        return true;
    }

    //Checking Biometric generated using new or old version
    public boolean isBiometricHavingOthers(List<BIR> bir){
        return bir.stream()
                .anyMatch(bi -> bi.getOthers() != null && !bi.getOthers().isEmpty());
    }

    //checking is ALL biometric is with exception
    public boolean isAllBioWithException(InternalRegistrationStatusDto registrationStatusDto) throws Exception {
    String uin=packetManagerService.getField(registrationStatusDto.getRegistrationId(),MappingJsonConstants.UIN,registrationStatusDto.getRegistrationType(),ProviderStageName.BIO_DEDUPE);
    BiometricRecord bm=getBiometricRecordfromIdrepo(uin);
    return allBiometricHaveException(bm.getSegments());
    }

    public String dateOfBirthFormatter(String dateStr) throws ParseException {
        SimpleDateFormat inputFormatter=new SimpleDateFormat(IDREPODATEFORMAT);
        SimpleDateFormat targetFormatter=new SimpleDateFormat(dobFormat);
        try {
            Date inputdate = inputFormatter.parse(dateStr);
            String convertedDate = targetFormatter.format(inputdate);
            regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.APPLICATIONID.toString(),
                    "Converted date: " + convertedDate, "");
//            Date date=convertToDate(parseDate(convertedDate));
            return convertedDate;
        } catch (ParseException e) {
            regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.APPLICATIONID.toString(),
                    "Failed to parse or convert date: " + dateStr, e.getMessage());
            throw e;
        }
    }
}
