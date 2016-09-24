/**
 * Autogenerated by Thrift
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 */
package com.leansoft.luxun.api.generated;


import java.util.Map;
import java.util.HashMap;
import org.apache.thrift.TEnum;

public enum ErrorCode implements org.apache.thrift.TEnum {
  INTERNAL_ERROR(0),
  TOPIC_NOT_EXIST(1),
  INDEX_OUT_OF_BOUNDS(2),
  INVALID_TOPIC(3),
  TOPIC_IS_EMPTY(4),
  AUTHENTICATION_FAILURE(5),
  MESSAGE_SIZE_TOO_LARGE(6),
  ALL_MESSAGE_CONSUMED(7);

  private final int value;

  private ErrorCode(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  public static ErrorCode findByValue(int value) { 
    switch (value) {
      case 0:
        return INTERNAL_ERROR;
      case 1:
        return TOPIC_NOT_EXIST;
      case 2:
        return INDEX_OUT_OF_BOUNDS;
      case 3:
        return INVALID_TOPIC;
      case 4:
        return TOPIC_IS_EMPTY;
      case 5:
        return AUTHENTICATION_FAILURE;
      case 6:
        return MESSAGE_SIZE_TOO_LARGE;
      case 7:
        return ALL_MESSAGE_CONSUMED;
      default:
        return null;
    }
  }
}
