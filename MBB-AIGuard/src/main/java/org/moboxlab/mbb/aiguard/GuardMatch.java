package org.moboxlab.mbb.aiguard;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则命中结果
 */
public class GuardMatch {
    public String category = "";
    public int risk = 0;
    public boolean safety = false;
    public List<String> keywords = new ArrayList<>();
}
