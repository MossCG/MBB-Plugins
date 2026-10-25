package org.moboxlab.mbb.roleplay;

/**
 * balanced 表达审核三段式状态与默认配置测试。
 */
public class RoleplayExpressionReviewModeTestMain {
    public static void main(String[] args) {
        RoleplayConfig config = new RoleplayConfig();
        if (!"balanced".equals(config.expressionReviewMode)
                || Math.abs(config.expressionAutoApproveMinQuality - 0.80) > 0.0001
                || Math.abs(config.expressionBalancedRejectMaxQuality - 0.25) > 0.0001) {
            throw new AssertionError("balanced 审核默认配置不正确");
        }
        if ("approved".equals(RoleplayExpressionReviewService.reviewStatus(
                "balanced",false,0.95,0.80,0.25))
                || !"rejected".equals(RoleplayExpressionReviewService.reviewStatus(
                "balanced",false,0.95,0.80,0.25))) {
            throw new AssertionError("keep=false 必须拒绝");
        }
        if (!"rejected".equals(RoleplayExpressionReviewService.reviewStatus(
                "balanced",true,0.10,0.80,0.25))
                || !"pending".equals(RoleplayExpressionReviewService.reviewStatus(
                "balanced",true,0.50,0.80,0.25))
                || !"approved".equals(RoleplayExpressionReviewService.reviewStatus(
                "balanced",true,0.80,0.80,0.25))) {
            throw new AssertionError("balanced 低质拒绝、中间待审、高质通过判断不正确");
        }
        if (!"pending".equals(RoleplayExpressionReviewService.reviewStatus(
                "suggest",true,0.99,0.80,0.25))
                || !"rejected".equals(RoleplayExpressionReviewService.reviewStatus(
                "auto",true,0.50,0.80,0.25))
                || !"approved".equals(RoleplayExpressionReviewService.reviewStatus(
                "auto",true,0.80,0.80,0.25))) {
            throw new AssertionError("suggest/auto 兼容行为不正确");
        }
        if (!"balanced".equals(RoleplayExpressionReviewService.normalizeMode("unknown"))) {
            throw new AssertionError("未知审核模式应回退 balanced");
        }
    }
}
