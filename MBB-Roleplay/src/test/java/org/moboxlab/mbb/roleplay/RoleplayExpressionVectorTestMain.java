package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;

/**
 * 表达候选向量配置、余弦相似度、文本相似度和向量解析测试。
 */
public class RoleplayExpressionVectorTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!config.expressionVectorEnable || !config.expressionReviewEnable
                || !"suggest".equals(config.expressionReviewMode)
                || config.expressionVectorTopK != 6
                || config.expressionReviewIntervalHours != 24) {
            throw new AssertionError("表达向量与审核默认配置不正确");
        }

        JSONArray x = vector(1.0,0.0);
        JSONArray y = vector(0.0,1.0);
        if (Math.abs(RoleplayExpressionVectorService.cosine(x,x) - 1.0) > 0.0001
                || Math.abs(RoleplayExpressionVectorService.cosine(x,y)) > 0.0001) {
            throw new AssertionError("余弦相似度计算错误");
        }

        double related = RoleplayExpressionVectorService.lexicalSimilarity(
                "箱子风道又反了","这个箱子风道是不是反了");
        double unrelated = RoleplayExpressionVectorService.lexicalSimilarity(
                "箱子风道又反了","今晚吃咖喱饭");
        if (related <= unrelated) {
            throw new AssertionError("相关表达的文本相似度应更高");
        }

        RoleplayExpressionVectorService service =
                new RoleplayExpressionVectorService(null,config);
        JSONArray parsed = service.parseVector("[0.1,0.2,-0.3]");
        if (parsed == null || parsed.size() != 3
                || Math.abs(parsed.getDoubleValue(2) + 0.3) > 0.0001) {
            throw new AssertionError("embedding 向量 JSON 解析错误");
        }
    }

    private static JSONArray vector(double first,double second) {
        JSONArray result = new JSONArray();
        result.add(first);
        result.add(second);
        return result;
    }
}
