import { Http, Headers, RequestOptions } from '@angular/http'
import { Injectable } from '@angular/core'
import { TranslateService } from '@ngx-translate/core'

import { environment as env } from '../../environments/environment'
import { Observable } from 'rxjs'
import 'rxjs/add/operator/map'

import { Dataset } from '../model2/dataset'
import { GrowlMessageService } from '../services-common/growl-message.service'
import { InstanceVariable } from '../model2/instance-variable'
import { NodeUtils } from '../utils/node-utils'
import { PopulationService } from '../services-common/population.service'
import { Study } from '../model2/study'

@Injectable()
export class EditorStudyService {

  constructor(
    private populationService: PopulationService,
    private translateService: TranslateService,
    private growlMessageService: GrowlMessageService,
    private nodeUtils: NodeUtils,
    private http: Http) {}

  public initNew(): Study {
    return this.initializeProperties({
      id: null,
      prefLabel: null,
      altLabel: null,
      abbreviation: null,
      description: null,
      usageConditionAdditionalInformation: null,
      freeConcepts: null,
      registryPolicy: null,
      purposeOfPersonRegistry: null,
      personRegistrySources: null,
      personRegisterDataTransfers: null,
      personRegisterDataTransfersOutsideEuOrEea: null,
      groundsForConfidentiality: null,
      principlesForPhysicalSecurity: [],
      principlesForDigitalSecurity: [],

      personInRoles: [],
      links: [],
      conceptsFromScheme: [],
      datasetTypes: [],
      datasets: [],
      predecessors: [],
      successors: []
    })
  }

  initializeProperties(study: Study): Study {
    this.initProperties(study, [
      'prefLabel',
      'altLabel',
      'abbreviation',
      'description',
      'usageConditionAdditionalInformation',
      'freeConcepts',
      'registryPolicy',
      'purposeOfPersonRegistry',
      'personRegistrySources',
      'personRegisterDataTransfers',
      'personRegisterDataTransfersOutsideEuOrEea',
      'groundsForConfidentiality'
    ])

    if (!study.population) {
      study.population = this.populationService.initNew()
    }

    return study
  }

  private initProperties(node: any, properties: string[]): void {
    this.nodeUtils.initLangValuesProperties(node, properties, [ this.translateService.currentLang ])
  }

  getAll(): Observable<Study[]> {
    return this.http.get(env.contextPath + '/api/v3/editor/studies')
      .map(response => response.json() as Study[])
  }

  getStudy(id: string): Observable<Study> {
    return this.http.get(env.contextPath + '/api/v3/editor/studies/' + id)
      .map(response => response.json() as Study)
  }

  save(study: Study): Observable<Study> {
    return this.saveStudyInternal(study)
      .do(dataset => {
        this.growlMessageService.buildAndShowMessage('success', 'operations.study.save.result.success')
      })
  }

  private saveStudyInternal(study: Study): Observable<Study> {
    const headers = new Headers({ 'Content-Type': 'application/json;charset=UTF-8' })
    const options = new RequestOptions({ headers: headers })

    return this.http.post(env.contextPath + '/api/v3/editor/studies', study, options)
      .map(response => response.json() as Study)
  }

  saveDataset(studyId: string, dataset: Dataset): Observable<Dataset> {
    const headers = new Headers({ 'Content-Type': 'application/json;charset=UTF-8' })
    const options = new RequestOptions({ headers: headers })

    return this.http.post(env.contextPath
      + '/api/v3/editor/studies/'
      + studyId
      + '/datasets', dataset, options)
      .map(response => response.json() as Dataset)
      .do(dataset => {
        this.growlMessageService.buildAndShowMessage('success', 'operations.dataset.save.result.success')
      })
  }

  saveInstanceVariable(studyId: string, datasetId: string, instanceVariable: InstanceVariable): Observable<InstanceVariable> {
    const headers = new Headers({ 'Content-Type': 'application/json;charset=UTF-8' })
    const options = new RequestOptions({ headers: headers })

    const url = env.contextPath
      + '/api/v3/editor/studies/'
      + studyId
      + '/datasets/'
      + datasetId
      + '/intanceVariables'

    return this.http.post(url, instanceVariable, options)
      .map(response => response.json() as InstanceVariable)
      .do(instanceVariables => {
        this.growlMessageService.buildAndShowMessage('success', 'operations.instanceVariable.save.result.success')
      })
  }

  publish(study: Study): Observable<Study> {
    const headers = new Headers({ 'Content-Type': 'application/json;charset=UTF-8' })
    const options = new RequestOptions({ headers: headers })

    return this.http.post(env.contextPath + '/api/v3/editor/study-functions/publish?studyId=' + study.id, {}, options)
      .map(response => response.json() as Study)
  }

  withdraw(study: Study): Observable<Study> {
    const headers = new Headers({ 'Content-Type': 'application/json;charset=UTF-8' })
    const options = new RequestOptions({ headers: headers })

    return this.http.post(env.contextPath + '/api/v3/editor/study-functions/withdraw?studyId=' + study.id, {}, options)
      .map(response => response.json() as Study)
  }

}
