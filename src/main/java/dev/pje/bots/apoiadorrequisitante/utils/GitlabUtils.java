package dev.pje.bots.apoiadorrequisitante.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.devplatform.model.gitlab.GitlabLabel;
import com.devplatform.model.gitlab.vo.GitlabMergeRequestVO;

public class GitlabUtils {	
	public static String changePomXMLVersion(String actualVersion, String newVersion, String pomxml, String tagPath) {
		return Utils.changeElementValueFromXML(pomxml, tagPath, actualVersion, newVersion);
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

	public static List<GitlabMergeRequestVO> getMergeRequestVOListFromString(String mergesWebUrls, String serverUrl){
		List<GitlabMergeRequestVO> MRs = new ArrayList<>();
		if(StringUtils.isNotBlank(mergesWebUrls)) {
			String[] weburls = mergesWebUrls.split(",");
			for (String weburl: weburls) {
				if(StringUtils.isNotBlank(weburl)) {
					String projectNamespace = getMergeProjectNamespaceFromWebUrl(serverUrl, weburl.trim());
					String mergeIId = getMergeIIdFromWebUrl(weburl.trim());
					GitlabMergeRequestVO mr = new GitlabMergeRequestVO(projectNamespace, mergeIId);
					if(!MRs.contains(mr)) {
						MRs.add(mr);
					}
				}
			}
		}
		return MRs;
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

	public static String getMergeProjectNamespaceFromWebUrl(String serverUrl, String weburl) {
		String projectNamespace = null;
		if(StringUtils.isNotBlank(weburl)) {
			String weburlWithoutServer = null;
			if(StringUtils.isNotBlank(serverUrl)) {
				weburlWithoutServer = weburl.replace(serverUrl, "");
			}else {
				weburlWithoutServer = weburl.replaceFirst("$./", "");
			}
			if(StringUtils.isNotBlank(weburlWithoutServer)) {
				if(weburlWithoutServer.startsWith("/")) {
					weburlWithoutServer.replaceFirst("/", "");
					List<String> projectNamespaceList = new ArrayList<>();
					String[] paths = weburlWithoutServer.split("/");
					for (String path : paths) {
						if(StringUtils.isNotBlank(path)) {
							if(path.equals("-")) {
								break;
							}else {
								projectNamespaceList.add(path);
							}
						}
					}
					if(projectNamespaceList != null && !projectNamespaceList.isEmpty()) {
						projectNamespace = String.join("/", projectNamespaceList);
					}
				}
			}
		}
		return projectNamespace;
	}
	
	public static String getGitlabProjectReleasesUrl(String projectWebUrl) {
		String releasesUrl = null;
		if(StringUtils.isNotBlank(projectWebUrl)) {
			if(!projectWebUrl.endsWith("/")) {
				projectWebUrl += "/";
			}
			releasesUrl = projectWebUrl + "-/releases";
		}
		return releasesUrl;
	}

	public static String getGitlabProjectJobsUrl(String jobId, String projectWebUrl) {
		String releasesUrl = null;
		if(StringUtils.isNotBlank(projectWebUrl)) {
			if(!projectWebUrl.endsWith("/")) {
				projectWebUrl += "/";
			}
			releasesUrl = projectWebUrl + "-/jobs/" + jobId;
		}
		return releasesUrl;
	}

	public static List<String> translateGitlabLabelListToValueList(List<GitlabLabel> gitlabLabels){
		List<String> labelList = new ArrayList<>();

		if(gitlabLabels != null) {
			for (GitlabLabel gitlabLabel : gitlabLabels) {
				labelList.add(gitlabLabel.getTitle());
			}
		}
		
		return labelList;
	}
}
