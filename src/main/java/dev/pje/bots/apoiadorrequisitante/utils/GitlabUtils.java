package dev.pje.bots.apoiadorrequisitante.utils;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class GitlabUtils {
	public static String getVersionFromPomXML(String pomxml, String versionTagPath) {
		String version = "";
		try {
	        DocumentBuilderFactory dbf =
	            DocumentBuilderFactory.newInstance();
	        DocumentBuilder db = dbf.newDocumentBuilder();
	        InputSource is = new InputSource();
	        is.setCharacterStream(new StringReader(pomxml));
	
	        Document doc = db.parse(is);

			String firstTagName = getFirstElementFromTagPath(versionTagPath);
			String tagNamePath = getTagPathWithoutFirstElement(versionTagPath);
			NodeList project = doc.getElementsByTagName(firstTagName);

	        version = getElementByTagName(project, tagNamePath);
	    }
	    catch (Exception e) {
	        e.printStackTrace();
	    }
		return version;
	}
	
	public static String getElementByTagName(NodeList nodeList, String tagPath) throws Exception {
		String foundedValue = null;
		String nextTagName = getFirstElementFromTagPath(tagPath);
		String subTagPath = getTagPathWithoutFirstElement(tagPath);
		
		try {
	        if(nodeList.getLength() > 0 && StringUtils.isNotBlank(nextTagName)) {
	        	Element element = (Element) nodeList.item(0);
	        	NodeList subNodeList = element.getElementsByTagName(nextTagName);
	        	if(StringUtils.isNotBlank(subTagPath)) {
	        		foundedValue = getElementByTagName(subNodeList, subTagPath);
	        	}else if(subNodeList.getLength() > 0) {
		        	Element line = (Element) subNodeList.item(0);
		        	foundedValue = getCharacterDataFromElement(line);
	        	}
	        }
		}catch (Exception e) {
			throw new Exception(e.getLocalizedMessage());
		}
		return foundedValue;
	}

	public static String changePomXMLVersion(String actualVersion, String newVersion, String pomxml, String tagPath) {
		String tagName = getLastElementFromTagPath(tagPath);
		String newContent = pomxml.replaceFirst("(<" + tagName + ">)" + actualVersion + "(</" + tagName + ">)", "$1"+ newVersion +"$2");
		return newContent;
	}
	
	public static String getCharacterDataFromElement(Element e) {
	    Node child = e.getFirstChild();
	    if (child instanceof CharacterData) {
	       CharacterData cd = (CharacterData) child;
	       return cd.getData();
	    }
	    return "?";
	}
	
	public static String getFirstElementFromTagPath(String tagPath) {
		String firstElement = null;
		String[] tagPathArray = tagPath.split(":");
		if(tagPathArray.length > 0) {
			firstElement = tagPathArray[0];
		}
		return firstElement;
	}

	public static String getLastElementFromTagPath(String tagPath) {
		String lastElement = null;
		String[] tagPathArray = tagPath.split(":");
		if(tagPathArray.length > 0) {
			lastElement = tagPathArray[(tagPathArray.length - 1)];
		}
		return lastElement;
	}

	public static String getTagPathWithoutFirstElement(String tagPath) {
		String subPath = null;
		String[] tagPathArray = tagPath.split(":");
		if(tagPathArray.length > 0) {
			int i = 0;
			List<String> subPathList = new ArrayList<>();
			for (String tagPathElement : tagPathArray) {
				if(i > 0) {
					subPathList.add(tagPathElement);
				}
				i++;
			}
			subPath = String.join(":", subPathList);
		}
		return subPath;
	}
	
	public static List<String> getMergeIIdListFromString(String merges){
		List<String> mergeIIds = new ArrayList<>();
		if(StringUtils.isNotBlank(merges)) {
			String[] weburls = merges.split(",");
			for (String weburl: weburls) {
				if(StringUtils.isNotBlank(weburl)) {
					String mergeIId = getMergeIIdFromWebUrl(weburl.trim());
					if(!mergeIIds.contains(mergeIId)) {
						mergeIIds.add(mergeIId);
					}
				}
			}
		}
		return mergeIIds;
	}
	
	public static String getMergeIIdFromWebUrl(String weburl) {
		String mergeIId = null;
		if(StringUtils.isNotBlank(weburl)) {
			String[] path = weburl.split("/");
			if(path != null && path.length > 0) {
				mergeIId = path[path.length - 1];
			}
		}
		return mergeIId;
	}
}
