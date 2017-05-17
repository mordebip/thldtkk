package fi.thl.thldtkk.api.metadata.domain.termed;

import static fi.thl.thldtkk.api.metadata.util.RegularExpressions.ALL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.joda.time.DateTime;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class PropertyMappings {

  private static final Logger log = LoggerFactory.getLogger(PropertyMappings.class);
    
  private PropertyMappings() {
  }

  public static StrictLangValue toPropertyValue(Integer integer) {
    Objects.requireNonNull(integer);
    return new StrictLangValue(String.valueOf(integer));
  }

  public static StrictLangValue toPropertyValue(String string) {
    Objects.requireNonNull(string);
    return new StrictLangValue(string);
  }

  public static StrictLangValue toPropertyValue(Boolean bool) {
    Objects.requireNonNull(bool);
    return new StrictLangValue("", String.valueOf(bool), "^(true|false)$");
  }

  public static StrictLangValue toPropertyValue(LocalDate date) {

    Objects.requireNonNull(date);

    String formattedDate = DateTimeFormatter.ISO_LOCAL_DATE.format(date);
    return new StrictLangValue("", formattedDate, "^\\d{4}-\\d{2}-\\d{2}$");
  }

  public static Collection<StrictLangValue> toPropertyValues(Map<String, String> localizedString) {
    return localizedString.entrySet().stream()
      .map(e -> new StrictLangValue(e.getKey(), e.getValue(), ALL))
      .collect(toList());
  }

  public static Map<String, String> toLangValueMap(Collection<StrictLangValue> propertyValues) {
    return propertyValues.stream()
      .collect(toMap(StrictLangValue::getLang, StrictLangValue::getValue));
  }

  public static String toString(Collection<StrictLangValue> values) {
    return values.stream()
      .map(StrictLangValue::getValue)
      .collect(Collectors.joining(", "));
  }

  public static Integer toInteger(Collection<StrictLangValue> values, Integer defaultValue) {
    return values.stream()
      .map(StrictLangValue::getValue)
      .findFirst()
      .map(Integer::parseInt)
      .orElse(defaultValue);
  }

  public static Boolean toBoolean(Collection<StrictLangValue> values, Boolean defaultValue) {
    return values.stream()
      .map(StrictLangValue::getValue)
      .findFirst()
      .map(Boolean::valueOf)
      .orElse(defaultValue);
  }

  public static LocalDate toLocalDate(Collection<StrictLangValue> values, LocalDate defaultValue) {
      return values.stream()
      .map(StrictLangValue::getValue)
      .findFirst()
      .map(value -> LocalDate.parse(value))
      .orElse(defaultValue);
  }

}
