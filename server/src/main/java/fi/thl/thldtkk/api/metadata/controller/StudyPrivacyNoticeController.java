package fi.thl.thldtkk.api.metadata.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Qualifier;

import fi.thl.thldtkk.api.metadata.service.report.StudyReportService;

import java.util.UUID;

@Controller
@RequestMapping(API.PATH_WITH_VERSION + "/editor/studies")

public class StudyPrivacyNoticeController {

  @Autowired
  @Qualifier("privacy-notice")
  private StudyReportService privacyNoticeReportService;

  @Autowired
  @Qualifier("scientific-privacy-notice")
  private StudyReportService scientificPrivacyNoticeReportService;

  @RequestMapping(value = "/{studyId}/privacy-notice", produces = MediaType.APPLICATION_PDF_VALUE)
  public @ResponseBody
  byte[] generatePdf(
    @PathVariable UUID studyId,
    @RequestParam(value = "lang", defaultValue = "fi") String lang
  ) throws Exception {
    return privacyNoticeReportService.generatePDFReport(studyId, lang);
  }

  @RequestMapping(value = "/{studyId}/scientific-privacy-notice", produces = MediaType.APPLICATION_PDF_VALUE)
  public @ResponseBody
  byte[] generateScientificPdf(
    @PathVariable UUID studyId,
    @RequestParam(value = "lang", defaultValue = "fi") String lang
  ) throws Exception {
    return scientificPrivacyNoticeReportService.generatePDFReport(studyId, lang);
  }
}
