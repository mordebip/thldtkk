package fi.thl.thldtkk.api.metadata.controller;

import fi.thl.thldtkk.api.metadata.domain.SupplementaryDigitalSecurityPrinciple;
import fi.thl.thldtkk.api.metadata.service.SupplementaryDigitalSecurityPrincipleService;
import fi.thl.thldtkk.api.metadata.util.spring.annotation.GetJsonMapping;
import fi.thl.thldtkk.api.metadata.util.spring.annotation.PostJsonMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

@RestController
@RequestMapping(API.PATH_WITH_VERSION + "/supplementaryDigitalSecurityPrinciples")
public class SupplementaryDigitalSecurityPrincipleController {

  @Autowired
  private SupplementaryDigitalSecurityPrincipleService supplementaryDigitalSecurityPrincipleService;

  @GetJsonMapping
  public List<SupplementaryDigitalSecurityPrinciple> query(@RequestParam(value = "query", defaultValue = "") String query, @RequestParam(value = "max", defaultValue = "-1") Integer max) {
    return supplementaryDigitalSecurityPrincipleService.find(query, max);
  }

  @PostJsonMapping(produces = APPLICATION_JSON_UTF8_VALUE)
  public SupplementaryDigitalSecurityPrinciple save(@RequestBody @Valid SupplementaryDigitalSecurityPrinciple principle) {
    return supplementaryDigitalSecurityPrincipleService.save(principle);
  }
}
