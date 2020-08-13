package dev.pje.bots.apoiadorrequisitante.utils;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriUtils;

import com.devplatform.model.bot.VersionReleaseNotes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;

public class Utils {
	public static final String DATE_SIMPLE_PATTERN = "yyyy-MM-dd";
	
	public static boolean compareAsciiIgnoreCase(String valueA, String valueB) {
	    String valueAprepared = StringUtils.stripAccents(valueA);
	    String valueBprepared = StringUtils.stripAccents(valueB);
	    
	    return valueAprepared.equalsIgnoreCase(valueBprepared);
	}
	
	public static boolean compareListValues(List<String> listA, List<String> listB) {
		if(listA != null && listB != null) {
			return listA.containsAll(listB) && listA.size() == listB.size();
		}
		return (listA == null && listB == null);
	}
	
	public static String escapeTelegramMarkup(String text) {
		Pattern regexCharacters = Pattern.compile("[(_\\*\\[\\]\\(\\)\\~`\\>\\#\\+\\-\\=\\|\\{\\}\\.\\!)?*+.]");
		return replaceTokens(text, regexCharacters);
	}
	
	public static String replaceTokens(String original, Pattern regexCharacters) {
		int lastIndex = 0;
		StringBuilder output = new StringBuilder();
		Matcher matcher = regexCharacters.matcher(original);
		while (matcher.find()) {
		    output.append(original, lastIndex, matcher.start())
		      .append("\\" + matcher.group(0));
		 
		    lastIndex = matcher.end();
		}
		if (lastIndex < original.length()) {
		    output.append(original, lastIndex, original.length());
		}
		return output.toString();
	}

	public static String calculateNextOrdinaryVersion(String currentVersion, int changeIndex) {
		String[] currentVersionArr = currentVersion.split("\\.");
		String nextVersion = "";
		
		for(int i=0; i < currentVersionArr.length && i < changeIndex; i++) {
			if(i > 0) {
				nextVersion += ".";
			}
			nextVersion += currentVersionArr[i];
		}
		
		if(changeIndex > 0) {
			nextVersion += ".";
		}
		Integer changedItem = 0;
		if(currentVersionArr.length >= changeIndex) {
			String changedItemStr = currentVersionArr[changeIndex];
			changedItem = Integer.valueOf(changedItemStr) + 1;
		}
		nextVersion += changedItem.toString();
		
		switch (changeIndex) {
		case 0:
			nextVersion += ".0.0.0";
			break;
		case 1:
			nextVersion += ".0.0";
			break;
		case 2:
			nextVersion += ".0";
			break;
		}
		
		return nextVersion;
	}
	
	public static String clearVersionNumber(String version) {
		return version.replaceAll("\\-SNAPSHOT", "");
	}
	
	public static String convertObjectToJson(Object obj) {
		ObjectMapper mapper = new ObjectMapper(); 
		String jsonStr = null;
        try { 
            jsonStr = mapper.writeValueAsString(obj); 
        }catch (IOException e) { 
            e.printStackTrace(); 
        }
        return jsonStr;
	}
	
	public static VersionReleaseNotes convertJsonToJiraReleaseNotes(String jsonString) {
		return convertJsonToObject(jsonString, VersionReleaseNotes.class);
	}

	public static <T> T convertJsonToObject(String jsonString, Class<T> valueType) {
		try {
			return (T) new ObjectMapper().readValue(jsonString, valueType);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String clearSummary(String summary) {
		String summaryCleaned = summary.replaceAll("\\[.*\\]", "").replaceAll("^[ ]*\\-", "").trim();
		if(StringUtils.isNotBlank(summaryCleaned)) {
			summaryCleaned = summaryCleaned.substring(0, 1).toUpperCase() + summaryCleaned.substring(1);
		}
		
		return summaryCleaned;
	}
	
	public static Date stringToDate(String stringDate, String pattern) throws ParseException {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
		if(StringUtils.isNotBlank(pattern)) {
			formatter = DateTimeFormatter.ofPattern(pattern);
		}
		ZonedDateTime zonedDateTime = ZonedDateTime.parse(stringDate, formatter);
		
		return Date.from(zonedDateTime.toInstant());
	}
	
	public static Date getDateFromString(String releaseDateStr) {
		Date date = null;
		try {
			date = Utils.stringToDate(releaseDateStr, null);
		}catch (Exception e) {
			try {
				date = Utils.stringToDate(releaseDateStr, JiraService.JIRA_DATETIME_PATTERN);
			}catch (Exception e1) {
				try {
					date = Utils.stringToDate(releaseDateStr, GitlabService.GITLAB_DATETIME_PATTERN);
				}catch (Exception e2) {
					try {
						date = Utils.stringToDate(releaseDateStr, DATE_SIMPLE_PATTERN);						
					}catch (Exception e3) {
						e3.getStackTrace();
					}
				}
			}
		}
		return date;
	}

	
	public static String dateToStringPattern(Date date, String pattern) {
		DateFormat df = new SimpleDateFormat(pattern);
		return df.format(date);
	}
	
	public static String getPathFromFilePath(String filePath) {
		String[] scriptPath = filePath.split("/");
		List<String> dirs = new ArrayList<String>();
		if(scriptPath.length > 0) {
			for (int i=0; (i < (scriptPath.length - 1)); i++) {
				dirs.add(scriptPath[i]);
			}
		}
		return String.join("/", dirs);
	}
	
	public static String getFileNameFromFilePath(String filePath) {
		String[] scriptPath = filePath.split("/");
		String fileName = "";
		if(scriptPath.length > 0) {
			fileName = scriptPath[scriptPath.length - 1];
		}
		return fileName;
	}
	
	public static String normalizePaths(String filePath) {
		String normalizedPath = null;
		
		if(StringUtils.isNotBlank(filePath)) {
			String changedPath = filePath;
			while(!changedPath.equals(normalizedPath)) {
				normalizedPath = changedPath;
				changedPath = changedPath.replaceAll("//", "/");
			}
		}
		
		return normalizedPath;
	}

	public static String urlEncode(String text) throws UnsupportedEncodingException {
		String urlEncoded = null;
		if(StringUtils.isNotBlank(text)) {
			String[] paths = text.split("/");
			List<String> pathsList = new ArrayList<>();
			if(paths != null && paths.length > 0) {
				for (String path : paths) {
					String pathEncoded = UriUtils.encodePath(path, StandardCharsets.UTF_8.toString());
					pathsList.add(pathEncoded);
				}
			}
			String separator = URLEncoder.encode("/", StandardCharsets.UTF_8.toString());
			urlEncoded = String.join(separator, pathsList);
		}
		return urlEncoded;
	}
	
	public static String escapeGitlabMarkup(String text) throws UnsupportedEncodingException {
		return text.replaceAll(" ", urlEncode(" "));
	}
	
	public static int compareVersionsDesc(List<Integer> versionNumbersA, List<Integer> versionNumbersB) {
		int diff = 0;
		if(versionNumbersA != null && versionNumbersB != null) {
			for (int i=0; i < versionNumbersA.size(); i++) {
				if(i < versionNumbersB.size()) {
					diff = (versionNumbersA.get(i) - versionNumbersB.get(i));
				}else {
					diff = versionNumbersA.get(i) - 0; // versionB will be considered 0 in this case
				}
				if(diff != 0) {
					break;
				}
			}
		}
		return (-1) * diff; // order DESC
	}

	public static List<Integer> getVersionFromString(String version){
		return getVersionFromString(version, "\\.");
	}
	
	public static List<Integer> getVersionFromString(String version, String delimiter){
		List<Integer> versionNumbers = new ArrayList<>();
		boolean isValid = false;
		if(version != null && !version.isEmpty()) {
			String[] versionParts = version.split(delimiter);
			for(int i=0; i < versionParts.length; i++) {
				if(!StringUtils.isNumericSpace(versionParts[i])) {
					isValid = false;
					break;
				}
				versionNumbers.add(Integer.valueOf(versionParts[i]));
				isValid = true;
			}
		}
		return isValid ? versionNumbers : null;
	}
	
	public static String getIssueKeyFromCommitMessage(String commitMessage) {
		String issueKey = null;
		List<String> issueKeys = new ArrayList<>();

		Pattern pattern = Pattern.compile("([A-Za-z]+\\-[0-9]+)");

        Matcher matcher = pattern.matcher(commitMessage);
        while(matcher.find()) {
        	String k = matcher.group(1);
    		issueKeys.add(k);
    		if(StringUtils.isBlank(issueKey)) {// está recuperando a primeira issue encontrada em caso de haver mais de uma
    			issueKey = k;
    		}
        }
        
        return issueKey;
	}
	
	public static String getPathFromAsciidocLink(String adocLink) {
		String path = null;
		
        Pattern pattern = Pattern.compile("link:([\\w\\-_\\s/\\d]+.html)\\[.*\\]");
        path = pattern.matcher(adocLink).replaceAll("$1");
        
        return path;
	}
	
	public static void waitSeconds(Integer numSeconds) {
		try {
			TimeUnit.SECONDS.sleep(numSeconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean isNumeric(String strNum) {
	    if (StringUtils.isBlank(strNum)) {
	        return false;
	    }
	    try {
	        Integer.valueOf(strNum);
	    } catch (NumberFormatException nfe) {
	        return false;
	    }
	    return true;
	}
	
	public static String addOption(String actualContent, String newOption) {
		List<String> newContentOptions = new ArrayList<>();
		boolean isDuplicate = false;
		if(StringUtils.isNotBlank(actualContent)) {
			String[] actualOptions = actualContent.split(",");
			for (String option: actualOptions) {
				if(StringUtils.isNotBlank(option) && !newContentOptions.contains(option)) {
					newContentOptions.add(option.trim());
					if(!isDuplicate && StringUtils.isNotBlank(newOption) && Utils.compareAsciiIgnoreCase(option, newOption)) {
						isDuplicate = true;
					}
				}
			}
		}
		if(!isDuplicate && StringUtils.isNotBlank(newOption)) {
			newContentOptions.add(newOption.trim());
		}
		return String.join(", ", newContentOptions);
	}
	
	public static String textToBase64(String text) {
		String textEncoded = byteArrToBase64(text.getBytes());
		return textEncoded;
	}
	
	public static String byteArrToBase64(byte[] data) {
		String textEncoded = Base64.getEncoder().encodeToString(data);
		return textEncoded;
	}

	public static String decodeFromBase64(String textEndoded) {
		byte[] decodedBytes = Base64
				.getDecoder()
				.decode(textEndoded);
		return new String(decodedBytes);
	}
	
	public static void writeByteArrayIntoFile(byte[] data) {
		String outputFilePath = "/tmp/saida.png";
		File outputFile = new File(outputFilePath);

		try {
			FileUtils.writeByteArrayToFile(outputFile, data);
			System.out.println("Criou o arquivo: "+ outputFilePath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
	