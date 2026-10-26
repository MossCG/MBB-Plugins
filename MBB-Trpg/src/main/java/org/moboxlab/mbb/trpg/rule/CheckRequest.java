package org.moboxlab.mbb.trpg.rule;

/** 检定请求；具体类型由 {@link #type()} 区分。 */
public interface CheckRequest {

    /** 检定类型，取值见 {@link CheckTypes}。 */
    String type();
}
