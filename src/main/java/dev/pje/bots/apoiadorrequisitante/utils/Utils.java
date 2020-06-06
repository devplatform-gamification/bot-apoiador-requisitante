package dev.pje.bots.apoiadorrequisitante.utils;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class Utils {
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

}
