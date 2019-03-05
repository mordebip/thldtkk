package fi.thl.thldtkk.api.metadata.service.csv;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import fi.thl.thldtkk.api.metadata.domain.*;
import fi.thl.thldtkk.api.metadata.service.*;
import fi.thl.thldtkk.api.metadata.service.csv.exception.AmbiguousUnitSymbolException;
import fi.thl.thldtkk.api.metadata.service.csv.exception.UndefinedLabelException;
import fi.thl.thldtkk.api.metadata.service.csv.exception.UndefinedUnitSymbolException;
import fi.thl.thldtkk.api.metadata.service.csv.serialize.ConceptSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

import static fi.thl.thldtkk.api.metadata.Constants.DEFAULT_LANG;
import static fi.thl.thldtkk.api.metadata.domain.CodeList.CODE_LIST_TYPE_EXTERNAL;
import static fi.thl.thldtkk.api.metadata.domain.CodeList.CODE_LIST_TYPE_INTERNAL;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.commons.lang.StringUtils.trimToNull;

import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InstanceVariableCsvParser {

  private static final Logger LOG = LoggerFactory.getLogger(InstanceVariableCsvParser.class);

  private CodeListService codeListService;

  private UnitService unitService;

  private UnitTypeService unitTypeService;

  private QuantityService quantityService;

  private EditorDatasetService editorDatasetService;

  private VariableService variableService;

  private InstanceQuestionService instanceQuestionService;

  private ConceptService conceptService;

  @Autowired
  public InstanceVariableCsvParser(CodeListService codeListService, UnitService unitService,
                                   UnitTypeService unitTypeService, QuantityService quantityService,
                                   EditorDatasetService editorDatasetService, VariableService variableService,
                                   InstanceQuestionService instanceQuestionService, ConceptService conceptService) {
    this.codeListService = codeListService;
    this.unitService = unitService;
    this.unitTypeService = unitTypeService;
    this.quantityService = quantityService;
    this.editorDatasetService = editorDatasetService;
    this.variableService = variableService;
    this.instanceQuestionService = instanceQuestionService;
    this.conceptService = conceptService;
  }

  public ParsingResult<List<ParsingResult<InstanceVariable>>> parse(InputStream csv, String encoding) {

    List<ParsingResult<InstanceVariable>> results = new LinkedList<>();
    List<String> messages = new LinkedList<>();

    if (isBlank(encoding)) {
      messages.add("import.csv.error.noEncoding");
      return done(results, messages);
    }

    CsvMapper mapper = new CsvMapper();
    CsvSchema schema = CsvSchema.emptySchema()
      .withHeader()
      .withColumnSeparator(';');
    ObjectReader objectReader = mapper.readerFor(Map.class)
      .with(schema)
      .withFeatures(CsvParser.Feature.TRIM_SPACES);

    MappingIterator<Map<String, String>> iterator;
    try {
      InputStreamReader csvReader = new InputStreamReader(csv, encoding);
      iterator = objectReader.readValues(csvReader);
    }
    catch (UnsupportedEncodingException e) {
      LOG.warn("Invalid encoding: {}", encoding, e);
      messages.add("import.csv.error.unsupportedEncoding");
      return done(results, messages);
    }
    catch (IOException e) {
      LOG.warn("Failed to read CSV", e);
      messages.add("import.csv.error.unknown");
      return done(results, messages);
    }

    boolean firstRow = true;
    while (iterator.hasNext()) {
      Map<String,String> row = iterator.next();

      if (firstRow) {
        if (!row.keySet().contains("prefLabel")) {
          messages.add("import.csv.error.missingRequiredColumn.prefLabel");
          return done(results, messages);
        }
        firstRow = false;
      }

      handleRow(row, results);
    }

    return done(results, messages);
  }

  private ParsingResult<List<ParsingResult<InstanceVariable>>> done(List<ParsingResult<InstanceVariable>> results, List<String> messages) {
    return new ParsingResult<>(results, messages);
  }

  private void handleRow(Map<String, String> row, List<ParsingResult<InstanceVariable>> results) {
    InstanceVariable instanceVariable = new InstanceVariable();
    List<String> rowMessages = new LinkedList<>();

    boolean isRowValid = true;

    String prefLabel = row.get("prefLabel");
    if (isNotBlank(prefLabel)) {
      instanceVariable.getPrefLabel().put(DEFAULT_LANG, prefLabel);
    }
    else {
      isRowValid = false;
      rowMessages.add("import.csv.error.missingRequiredValue.rowOmitted|prefLabel");
    }

    sanitize(row.get("technicalName")).ifPresent(technicalName -> instanceVariable.setTechnicalName(technicalName));
    sanitize(row.get("description")).ifPresent(description -> instanceVariable.getDescription().put(DEFAULT_LANG,description));
    sanitize(row.get("partOfGroup")).ifPresent(partOfGroup -> instanceVariable.getPartOfGroup().put(DEFAULT_LANG, partOfGroup));
    sanitize(row.get("freeConcepts")).ifPresent(freeConcepts -> instanceVariable.getFreeConcepts().put(DEFAULT_LANG, freeConcepts));

    instanceVariable.setReferencePeriodStart(parseLocalDate(row, "referencePeriodStart", rowMessages));
    instanceVariable.setReferencePeriodEnd(parseLocalDate(row, "referencePeriodEnd", rowMessages));

    sanitize(row.get("valueDomainType")).ifPresent(valueDomainType -> instanceVariable.setValueDomainType(valueDomainType));
    sanitize(row.get("missingValues")).ifPresent((missingValues -> instanceVariable.getMissingValues().put(DEFAULT_LANG, missingValues)));
    sanitize(row.get("defaultMissingValue")).ifPresent(defaultMissingValue -> instanceVariable.setDefaultMissingValue(defaultMissingValue));
    sanitize(row.get("qualityStatement")).ifPresent(qualityStatement -> instanceVariable.getQualityStatement().put(DEFAULT_LANG, qualityStatement));

    // "sourceDescription" is legacy format, "source.description" is the new and correct one
    sanitize(row.get("sourceDescription")).ifPresent(sourceDescription -> instanceVariable.getSourceDescription().put(DEFAULT_LANG, sourceDescription));
    sanitize(row.get("source.description")).ifPresent(sourceDescription -> instanceVariable.getSourceDescription().put(DEFAULT_LANG, sourceDescription));

    sanitize(row.get("dataType")).ifPresent(dataType -> instanceVariable.setDataType(dataType));
    sanitize(row.get("dataFormat")).ifPresent(dataFormat -> instanceVariable.getDataFormat().put(DEFAULT_LANG, dataFormat));

    Optional<String> codeListType = sanitize(row.get("codeList.codeListType"));

    if (codeListType.isPresent()) {
      Optional<String> codeListPrefLabel = sanitize(row.get("codeList.prefLabel"));
      Optional<String> codeListDescription = sanitize(row.get("codeList.description"));
      Optional<String> codeListOwner = sanitize(row.get("codeList.owner"));

      if (codeListType.get().equals(CODE_LIST_TYPE_EXTERNAL)) {
        Optional<String> codeListReferenceId = sanitize(row.get("codeList.referenceId"));

        try {
            Optional<CodeList> codeList = getCodeList(codeListPrefLabel, codeListReferenceId, DEFAULT_LANG, rowMessages);
            if (!codeList.isPresent()) {
                codeList = createCodeList(rowMessages, DEFAULT_LANG, CODE_LIST_TYPE_EXTERNAL, codeListPrefLabel, codeListDescription, codeListOwner, codeListReferenceId, Optional.empty());
            }
            codeList.ifPresent(cl -> {
                instanceVariable.setCodeList(cl);
                instanceVariable.setValueDomainType(InstanceVariable.VALUE_DOMAIN_TYPE_ENUMERATED);
            });
        } catch (UndefinedLabelException e) {
            isRowValid = false;
            rowMessages.add("import.csv.error.missingRequiredValue.rowOmitted|codeList.prefLabel");
        }
      } else if (codeListType.get().equals(CODE_LIST_TYPE_INTERNAL)) {
        Optional<String> codeListItems = sanitize(row.get("codeList.codeItems"));

        try {
            Optional<CodeList> codeList = createCodeList(rowMessages, DEFAULT_LANG, CODE_LIST_TYPE_INTERNAL, codeListPrefLabel, codeListDescription, codeListOwner, Optional.empty(), codeListItems);

            codeList.ifPresent(cl -> {
                instanceVariable.setCodeList(cl);
                instanceVariable.setValueDomainType(InstanceVariable.VALUE_DOMAIN_TYPE_ENUMERATED);
            });
        } catch (UndefinedLabelException e) {
            isRowValid = false;
            rowMessages.add("import.csv.error.missingRequiredValue.rowOmitted|codeList.prefLabel");
        }
      }
    }

    Optional<String> unitTypePrefLabel = sanitize(row.get("unitType.prefLabel"));
    if (unitTypePrefLabel.isPresent() && !unitTypePrefLabel.get().isEmpty()) {
        Optional<String> unitTypeDescription = sanitize(row.get("unitType.description"));
        Optional<UnitType> unitType = unitTypeService.findByPrefLabel(unitTypePrefLabel.get());
        if (!unitType.isPresent()) {
            Map<String, String> prefLabelMap = new LinkedHashMap<>();
            Map<String, String> descriptionMap = new LinkedHashMap<>();

            prefLabelMap.put(DEFAULT_LANG, unitTypePrefLabel.get());
            if (isNotBlank(unitTypeDescription.get())) {
                descriptionMap.put(DEFAULT_LANG, unitTypeDescription.get());
            }
            UnitType unitTypeNew = new UnitType(randomUUID(), prefLabelMap, descriptionMap);
            unitType = Optional.ofNullable(unitTypeService.save(unitTypeNew));
        }
        unitType.ifPresent(instanceVariable::setUnitType);
    }

    Optional<String> quantityPrefLabel = sanitize(row.get("quantity.prefLabel"));
    if (quantityPrefLabel.isPresent() && !quantityPrefLabel.get().isEmpty()) {
        Optional<Quantity> quantity = quantityService.findByPrefLabel(quantityPrefLabel.get());
        if (!quantity.isPresent()) {
            Map<String, String> prefLabelMap = new LinkedHashMap<>();
            prefLabelMap.put(DEFAULT_LANG, quantityPrefLabel.get());
            Quantity quantityNew = new Quantity(randomUUID(), prefLabelMap);
            quantity = Optional.ofNullable(quantityService.save(quantityNew));
        }
        quantity.ifPresent(instanceVariable::setQuantity);
    }

    sanitize(row.get("valueRangeMin")).ifPresent(valueRangeMin -> instanceVariable.setValueRangeMin(getBigDecimalFromString(valueRangeMin, rowMessages, "valueRangeMin")));
    sanitize(row.get("valueRangeMax")).ifPresent(valueRangeMax -> instanceVariable.setValueRangeMax(getBigDecimalFromString(valueRangeMax, rowMessages, "valueRangeMax")));

    Optional<String> sourceDatasetPrefLabel = sanitize(row.get("source.dataset.prefLabel"));
    if (sourceDatasetPrefLabel.isPresent() && !sourceDatasetPrefLabel.get().isEmpty()) {
        Optional<Dataset> source = editorDatasetService.findByPrefLabel(sourceDatasetPrefLabel.get());
        if (source.isPresent()) {
            instanceVariable.setSource(source.get());
        } else {
            rowMessages.add("import.csv.error.missingRequiredValue.fieldMissingFromDb|source.dataset.prefLabel");
        }
    }

    Optional<String> variablePrefLabel = sanitize(row.get("variable.prefLabel"));
    if (variablePrefLabel.isPresent() && isNotBlank(variablePrefLabel.get())) {
        Optional<Variable> variable = variableService.findByPrefLabel(variablePrefLabel.get());
        if (!variable.isPresent()) {
            Map<String, String> prefLabelMap = new LinkedHashMap<>();
            prefLabelMap.put(DEFAULT_LANG, variablePrefLabel.get());
            
            Map<String, String> descriptionMap = new LinkedHashMap<>();
            Optional<String> variableDescription = sanitize(row.get("variable.description"));
            if (isNotBlank(variableDescription.get())) {
                descriptionMap.put(DEFAULT_LANG, variableDescription.get());
            }

            Variable variableNew = new Variable(null, prefLabelMap, descriptionMap);
            variable = Optional.ofNullable(variableService.save(variableNew));
        }
        variable.ifPresent(instanceVariable::setVariable);
    }

    Optional<String> instanceQuestionsString = sanitize(row.get("instanceQuestions"));
    if (instanceQuestionsString.isPresent() && isNotBlank(instanceQuestionsString.get())) {
      String[] instanceQuestions = instanceQuestionsString.get().split(ConceptSerializer.SEPARATOR);
      for (String instanceQuestionString : instanceQuestions) {
        Map<String, String> prefLabelMap = new LinkedHashMap<>();
        prefLabelMap.put(DEFAULT_LANG, instanceQuestionString);
        InstanceQuestion instanceQuestion = new InstanceQuestion(randomUUID(), prefLabelMap);
        InstanceQuestion savedInstanceQuestion = instanceQuestionService.save(instanceQuestion);
        instanceVariable.addInstanceQuestion(savedInstanceQuestion);
      }
    }

    Optional<String> conceptsFromSchemeString = sanitize(row.get("conceptsFromScheme"));
    if (conceptsFromSchemeString.isPresent() && isNotBlank(conceptsFromSchemeString.get())) {
        String[] conceptPrefLabels = conceptsFromSchemeString.get().split(ConceptSerializer.SEPARATOR);
        int wordNumber = 1;
        for (String conceptPrefLabel : conceptPrefLabels) {
            Optional<Concept> concept = conceptService.findByPrefLabel(conceptPrefLabel);
            if (concept.isPresent()) {
                instanceVariable.addConceptsFromScheme(concept.get());
            } else {
                rowMessages.add("import.csv.error.missingRequiredValue.fieldMissingFromDb|conceptFromScheme (" + wordNumber + ")");
            }
            wordNumber++;
        }
    }

    parseUnit(row, DEFAULT_LANG, rowMessages).ifPresent(unit -> instanceVariable.setUnit(unit));

    results.add(new ParsingResult<>(isRowValid ? instanceVariable : null, rowMessages));
  }

  private LocalDate parseLocalDate(Map<String, String> row, String field, List<String> rowMessages) {
    String dateString = row.get(field);
    LocalDate date = null;
    if (isNotBlank(dateString)) {
      try {
        date = LocalDate.parse(dateString);
      }
      catch (DateTimeParseException e) {
        rowMessages.add("import.csv.warn.invalidIsoDate|" + field);
      }
    }
    return date;
  }

  private Optional<String> sanitize(String columnEntry) {
    return Optional.ofNullable(columnEntry)
            .map(String::trim)
            .map(this::normalizeLineBreaks);
  }

  private String normalizeLineBreaks(String columnEntry) {
    String windowsStyleLineBreaksReplaced = columnEntry.replaceAll("\r\n", "\n"); // CR+LF -> LF
    String linefeedsOnlyReplaced = windowsStyleLineBreaksReplaced.replaceAll("\r", "\n"); // CR -> LF
    return linefeedsOnlyReplaced;
  }

  private List<CodeList> searchCodeListsByReferenceId(String referenceId) {
      return codeListService.findByExactReferenceId(referenceId, -1)
        .stream()
        .filter(codeList -> {
          Optional<String> storedReferenceId = codeList.getReferenceId();
          return storedReferenceId.isPresent() ? storedReferenceId.get().equalsIgnoreCase(referenceId) : false;
        })
        .collect(Collectors.toList());
  }

  private List<CodeList> searchCodeListsByLabel(String label, String language) {
      return codeListService.findByExactPrefLabel(label, -1)
        .stream()
        .filter(codeList -> {
          String storedLabel = codeList.getPrefLabel().get(language);
          return storedLabel != null ? storedLabel.equalsIgnoreCase(label) : false;
      }).collect(Collectors.toList());
  }

  private Optional<CodeList> getCodeList(Optional<String> label, Optional<String> referenceId, String language, List<String> rowMessages) {

      List<CodeList> bestMatches = new ArrayList<>();
      List<CodeList> codeListsByLabel = new ArrayList<>();
      List<CodeList> codeListsByReferenceId = new ArrayList<>();

      if(label.isPresent() && isNotBlank(label.get())) {
         codeListsByLabel = searchCodeListsByLabel(label.get(), language);
      }

      if(referenceId.isPresent() && isNotBlank(referenceId.get())) {
         codeListsByReferenceId = searchCodeListsByReferenceId(referenceId.get());
      }

      // match order (1.) label and ref. id; (2.) ref. id only; (3.) label only

      if(!codeListsByLabel.isEmpty() && !codeListsByReferenceId.isEmpty()) {
          for(CodeList referenceIdCodeList : codeListsByReferenceId) {
              if(codeListsByLabel.contains(referenceIdCodeList)) {
                  bestMatches.add(referenceIdCodeList);
              }
          }
      }

      if(bestMatches.isEmpty()) {
          if(!codeListsByReferenceId.isEmpty()) {
              bestMatches = codeListsByReferenceId;
          }

          else if (!codeListsByLabel.isEmpty()) {
              bestMatches = codeListsByLabel;
          }
      }

      if(bestMatches.size() > 1) {
          rowMessages.add("import.csv.warn.ambiguousCodeList|codeList.prefLabel"); // should fail import?
      }

      return bestMatches.isEmpty() ? Optional.empty() : Optional.of(bestMatches.get(0));
  }

  private Optional<CodeList> createCodeList(List<String> rowMessages, String language, String codeListType,
                                            Optional<String> label, Optional<String> description, Optional<String> owner,
                                            Optional<String> referenceId, Optional<String> codeListItems) throws UndefinedLabelException {

      if(!label.isPresent() || (label.isPresent() && isBlank(label.get())) ) {
          throw new UndefinedLabelException();
      }

      CodeList codeList = new CodeList();
      codeList.getPrefLabel().put(language, label.get());

      codeList.setCodeListType(codeListType);

      referenceId.ifPresent(codeList::setReferenceId);
      description.ifPresent(localizedDescription -> codeList.getDescription().put(language, localizedDescription));
      owner.ifPresent(localizedOwner -> codeList.getOwner().put(language, localizedOwner));

      if (codeListItems.isPresent() && isNotBlank(codeListItems.get())) {
          String[] codeListCodeItems = codeListItems.get().split(ConceptSerializer.SEPARATOR);

          int wordNumber = 1;
          for (String codeListCodeItem : codeListCodeItems) {
              String[] codeListCodeItemParts = codeListCodeItem.split(":");
              String codeItemCode = trimToNull(codeListCodeItemParts[0]);
              String codeItemPrefLabel = trimToNull(codeListCodeItemParts[1]);

              if (codeListCodeItemParts.length == 2 && codeItemCode != null && codeItemPrefLabel != null) {
                CodeItem codeItem = new CodeItem(randomUUID());
                codeItem.setCode(codeListCodeItemParts[0]);

                Map<String, String> prefLabelMap = new LinkedHashMap<>();
                prefLabelMap.put(language, codeItemPrefLabel);
                codeItem.setPrefLabel(prefLabelMap);

                codeList.addCodeItem(codeItem);
              } else {
                rowMessages.add("import.csv.warn.codeItemWrongFormat|codeList.codeItems (" + wordNumber + ")");
              }
              wordNumber++;
          }
      }

      return Optional.ofNullable(codeListService.save(codeList));
  }

    private Optional<Unit> parseUnit(Map<String, String> row, String language, List<String> rowMessages) {
    Optional<Unit> unit = Optional.empty();

    Optional<String> unitSymbol = sanitize(row.get("unit.symbol"));
    Optional<String> unitLabel = sanitize(row.get("unit.prefLabel"));

    if(unitSymbol.isPresent() && isNotBlank(unitSymbol.get())) {
      try {
        unit = searchUnitBySymbol(unitSymbol.get(), language);
        unit = unit.isPresent() ? unit : Optional.of(createUnit(unitLabel, unitSymbol, language));
      } catch (AmbiguousUnitSymbolException ex) {
        rowMessages.add("import.csv.warn.ambiguousUnitSymbol|unit.symbol");
      } catch (UndefinedLabelException ex) {
        rowMessages.add("import.csv.warn.missingRequiredValue|unit.prefLabel");
      } catch (UndefinedUnitSymbolException ex) {
        rowMessages.add("import.csv.warn.missingRequiredValue|unit.symbol");
      }
    }

    return unit;
  }

  private Optional<Unit> searchUnitBySymbol(String symbol, String language) throws AmbiguousUnitSymbolException{

    Optional<Unit> unit = Optional.empty();
    List<Unit> units = unitService.findBySymbol(symbol);

    if(units.size() == 1) {
      Iterator<Unit> iterator = units.listIterator();
      unit = Optional.of(iterator.next());
    }

    else if(units.size() > 1) {
      // case sensitive filtering for unit symbols (e.g. 'v' and 'V')
      units = units.stream().filter(u -> u.getSymbol().containsKey(language) &&
              u.getSymbol().get(language).equals(symbol))
              .collect(Collectors.toList());

      if(units.size() > 1) {
        throw new AmbiguousUnitSymbolException(symbol);
      }

      else {
        Iterator<Unit> iterator = units.listIterator();
        unit = Optional.of(iterator.next());
      }

    }

    return unit;
  }

  private Unit createUnit(Optional<String> label, Optional<String> symbol, String language) throws UndefinedLabelException, UndefinedUnitSymbolException {
    if(!label.isPresent() || isBlank(label.get())) {
      throw new UndefinedLabelException();
    }

    if(!symbol.isPresent() || isBlank(symbol.get())) {
      throw new UndefinedUnitSymbolException();
    }

    Unit unit = new Unit(randomUUID());
    unit.getPrefLabel().put(language, label.get());
    unit.getSymbol().put(language, symbol.get());
    return unitService.save(unit);
  }

  private BigDecimal getBigDecimalFromString(String numberString, List<String> rowMessages, String field) {
      if (isNotBlank(numberString)) {
          try {
              return new BigDecimal(numberString.replaceAll(",", "."));
          } catch (NumberFormatException e) {
              LOG.warn("Invalid format for decimal number: {}", numberString, e);
              rowMessages.add("import.csv.warn.invalidDecimalNumber|" + field);
          }
      }
      return null;
  }

}
