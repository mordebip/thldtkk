import {ActivatedRoute, Router} from '@angular/router';
import {AfterContentChecked, Component, OnInit, ViewChild} from '@angular/core'
import {NgForm, AbstractControl} from '@angular/forms'
import {Title} from '@angular/platform-browser'
import {TranslateService} from '@ngx-translate/core';
import {MultiSelect} from "primeng/primeng";
import {BreadcrumbService} from '../../../services-common/breadcrumb.service'
import {ConfidentialityClass} from '../../../model2/confidentiality-class'
import {SecurityPrincipleService} from "../../../services-common/security-principle.service";
import {DateUtils} from '../../../utils/date-utils'
import {EditorStudyService} from '../../../services-editor/editor-study.service'
import {EditorSystemService} from '../../../services-editor/editor-system.service'
import {EditorSystemRoleService} from '../../../services-editor/editor-system-role.service'
import {GrowlMessageService} from '../../../services-common/growl-message.service'
import {LangPipe} from '../../../utils/lang.pipe'
import {NodeUtils} from '../../../utils/node-utils';
import {Observable, Subscription} from 'rxjs';
import {PrincipleForDigitalSecurity} from '../../../model2/principle-for-digital-security'
import {PrincipleForPhysicalSecurity} from '../../../model2/principle-for-physical-security'
import {SelectItem} from 'primeng/components/common/api'
import {RetentionPolicy} from '../../../model2/retention-policy';
import {ExistenceForm} from '../../../model2/existence-form';
import {Study} from '../../../model2/study';
import {StudyType} from '../../../model2/study-type';
import {StudySidebarActiveSection} from './sidebar/study-sidebar-active-section'
import {System} from '../../../model2/system';
import {SystemRole} from '../../../model2/system-role';
import {SystemInRole} from '../../../model2/system-in-role';
import {LegalBasisForHandlingPersonalData} from '../../../model2/legal-basis-for-handling-personal-data';
import {LegalBasisForHandlingSensitivePersonalData} from '../../../model2/legal-basis-for-handling-sensitive-personal-data';
import {TypeOfSensitivePersonalData} from '../../../model2/type-of-sensitive-personal-data';
import { Organization } from 'app/model2/organization';
import { OrganizationService } from 'app/services-common/organization.service';
import { AssociatedOrganization } from 'app/model2/associated-organization';
import {PostStudyRetentionOfPersonalData} from '../../../model2/post-study-retention-of-personal-data'
import {SupplementaryDigitalSecurityPrinciple} from "../../../model2/supplementary-digital-security-principle";
import {SupplementaryPhysicalSecurityPrinciple} from "../../../model2/supplementary-physical-security-principle";


@Component({
    templateUrl: './study-administrative-edit.component.html',
    styleUrls: ['./study-administrative-edit.component.css'],
    providers: [LangPipe]
})
export class StudyAdministrativeEditComponent implements OnInit, AfterContentChecked {

    study: Study;
    StudyType = StudyType;
    selectableRetentionOptions: string[] = [];

    yearRangeForDataProcessingFields: string =  ('1900:' + (new Date().getFullYear() + 20))
    dataProcessingStartDate: Date
    dataProcessingEndDate: Date

    retentionPolicies: SelectItem[] = []
    existenceForms: SelectItem[] = []

    @ViewChild('studyForm') studyForm: NgForm
    currentForm: NgForm
    formErrors: any = {}

    language: string;

    savingInProgress: boolean = false
    savingHasFailed: boolean = false

    sidebarActiveSection = StudySidebarActiveSection.ADMINISTRATIVE_INFORMATION

    allOrganizations: Organization[];
    availableAssociatedOrganizations: Organization[];
    newAssociatedOrganization: Organization;

    confidentialityClassType = ConfidentialityClass

    principlesForPhysicalSecurityItems: SelectItem[] = []
    principlesForDigitalSecurityItems: SelectItem[] = []

    allSystemRoles: SystemRole[]
    allSystemItems: SelectItem[]

    systemInRoleForNewSystem: SystemInRole
    newSystem: System

    defaultSystemLinkDescription: string

    legalBasisForHandlingPersonalDataOptions: SelectItem[] = []
    legalBasisForHandlingSensitivePersonalDataOptions: SelectItem[] = []
    typeOfSensitivePersonalDataOptions: SelectItem[] = []

    newSupplementaryPhysicalSecurityPrinciple: SupplementaryPhysicalSecurityPrinciple
    newSupplementaryDigitalSecurityPrinciple: SupplementaryDigitalSecurityPrinciple

    otherPhysicalSecurityPrinciples: SelectItem[]
    otherDigitalSecurityPrinciples: SelectItem[]

    @ViewChild('otherDigitalSecuritySelect') otherDigitalSecuritySelect: MultiSelect

    constructor(
        private studyService: EditorStudyService,
        private systemService: EditorSystemService,
        private systemRoleService: EditorSystemRoleService,
        private organizationService: OrganizationService,
        private growlMessageService: GrowlMessageService,
        private route: ActivatedRoute,
        private router: Router,
        private breadcrumbService: BreadcrumbService,
        private translateService: TranslateService,
        private langPipe: LangPipe,
        private titleService: Title,
        private dateUtils: DateUtils,
        private nodeUtils: NodeUtils,
        private securityPrincipleService: SecurityPrincipleService
    ) {
        this.language = this.translateService.currentLang
    }


    ngOnInit() {
      this.getStudy()

      this.translateService.get('legalBasisForHandlingPersonalData')
        .subscribe(translations => {
          this.legalBasisForHandlingPersonalDataOptions = Object.keys(LegalBasisForHandlingPersonalData)
            .map(key => {
              return { label: translations[key], value: key }
            })
        })

      this.translateService.get('legalBasisForHandlingSensitivePersonalData')
        .subscribe(translations => {
          this.legalBasisForHandlingSensitivePersonalDataOptions = Object.keys(LegalBasisForHandlingSensitivePersonalData)
            .map(key => {
              return { label: translations[key], value: key }
            })
        })

      this.translateService.get('typeOfSensitivePersonalData')
        .subscribe(translations => {
          this.typeOfSensitivePersonalDataOptions = Object.keys(TypeOfSensitivePersonalData)
            .map(key => {
              return { label: translations[key], value: key }
            })
        })

      this.translateService.get('principlesForPhysicalSecurity')
        .subscribe(translations => {
          this.principlesForPhysicalSecurityItems = Object.keys(PrincipleForPhysicalSecurity)
            .map(key => {
              return { label: translations[key], value: key }
            })
        })

      this.translateService.get('principlesForDigitalSecurity')
        .subscribe(translations => {
          this.principlesForDigitalSecurityItems = Object.keys(PrincipleForDigitalSecurity)
            .map(key => {
              return { label: translations[key], value: key }
            })
        })

      this.translateService.get('editSystemModal.externalLink')
        .subscribe(translation =>  this.defaultSystemLinkDescription = translation)

      this.populateRetentionPolicies()
      this.populateExistenceForms()
      this.getAllSystemRoles()
      this.getAllSystems()
      this.getAllOrganizations()
      this.getAllSupplementaryPhysicalSecurityPrinciples()
      this.getAllSupplementaryDigitalSecurityPrinciples()
    }

    private getStudy() {
      const studyId = this.route.snapshot.params['studyId']
      if (studyId) {
        this.studyService.getStudy(studyId)
          .subscribe(study => {
            this.study = this.studyService.initializeProperties(study)
            this.study = this.initializeStudyAdministrativeProperties(study)
            this.updatePageTitle()
            this.breadcrumbService.updateEditorBreadcrumbsForStudyDatasetAndInstanceVariable(this.study)
            this.availableAssociatedOrganizations = this.getAvailableAssociatedOrganizations()
            this.updateSelectableRetentionOptions()
          })
      } else {
        this.study = this.studyService.initNew()
        this.availableAssociatedOrganizations = this.getAvailableAssociatedOrganizations()
        this.updateSelectableRetentionOptions()
      }
    }

    private updatePageTitle():void {
        if(this.study.prefLabel) {
            let translatedLabel:string = this.langPipe.transform(this.study.prefLabel)
            let bareTitle:string = this.titleService.getTitle();
            this.titleService.setTitle(translatedLabel + " - " + bareTitle)
        }
    }

    private initializeStudyAdministrativeProperties(study: Study): Study {
        if (study.dataProcessingStartDate) {
          this.dataProcessingStartDate = new Date(study.dataProcessingStartDate)
        }
        if (study.dataProcessingEndDate) {
          this.dataProcessingEndDate = new Date(study.dataProcessingEndDate)
        }
        return study;
    }

    private populateRetentionPolicies() {
      for(let policy in RetentionPolicy) {
        this.translateService.get('retentionPolicy.'+policy).subscribe(policyLabel => {
          if(policy == RetentionPolicy.UNDEFINED.toString()) {
            this.retentionPolicies.push({
                label: policyLabel,
                value: null
              })
          }
          else {
            this.retentionPolicies.push({
              label: policyLabel,
              value: policy
            })
          }
        })
      }
    }

    private populateExistenceForms() {
      for(let form in ExistenceForm) {
        this.translateService.get('existenceForm.'+form).subscribe(formLabel => {
            this.existenceForms.push({
              label: formLabel,
              value: form
            })
        })
      }
    }

    private getAllSystemRoles() {
      this.systemRoleService.getAll().subscribe(systemRoles => 
        this.allSystemRoles = systemRoles)
    }

    removeSystemInRole(systemInRole: SystemInRole) {
      let index: number = this.study.systemInRoles.indexOf(systemInRole)
      if (index !== -1) {
        this.study.systemInRoles.splice(index, 1)
      }
    }

    showAddSystemModal(systemInRole: SystemInRole): void {
      this.systemInRoleForNewSystem = systemInRole
      this.initNewSystem()
    }

    private initNewSystem(): void {
      this.newSystem = this.systemService.initNew()
      this.newSystem.ownerOrganization = this.study.ownerOrganization

    }

    saveSystem(event): void {
      if(this.newSystem.link && this.newSystem.link.linkUrl) {
        this.newSystem.link.prefLabel[this.language] = this.defaultSystemLinkDescription
      }

      this.systemService.save(this.newSystem)
        .subscribe(savedSystem => {
          this.getAllSystems()
          if (this.systemInRoleForNewSystem) {
            this.systemInRoleForNewSystem.system = savedSystem
          }
          this.closeAddSystemModal()
        })
    }

    closeAddSystemModal() {
      this.newSystem = null
      this.systemInRoleForNewSystem = null
    }

    private getAllSystems() {
      this.allSystemItems = []

      Observable.forkJoin(
        this.translateService.get('noSystem'),
        this.systemService.getAll()
      ).subscribe(data => {
        this.allSystemItems.push({
          label: data[0],
          value: null
        })
        data[1].forEach(system => this.allSystemItems.push({
          label: this.langPipe.transform(system.prefLabel),
          value: system
        }))
      })
    }

    private getAllOrganizations() {
      this.organizationService.getAllOrganizations()
        .subscribe(organizations => {
          this.allOrganizations = organizations
          this.availableAssociatedOrganizations = this.getAvailableAssociatedOrganizations()
        });
    }

    private getAvailableAssociatedOrganizations() {
      if (this.study && this.allOrganizations) {
        const organizationIds = this.study.associatedOrganizations.map(org => org.organization.id)
        if (this.study.ownerOrganization) {
          organizationIds.push(this.study.ownerOrganization.id)
        }

        return this.allOrganizations.filter(org => {
          return !organizationIds.includes(org.id)
        })
      }

      return []
    }

    addAssociatedOrganization() {
      if (this.newAssociatedOrganization != null) {
        this.study.associatedOrganizations.push({
          id: null,
          organization: this.newAssociatedOrganization,
          isRegistryOrganization: false
        })
      }
      this.newAssociatedOrganization = null
      this.availableAssociatedOrganizations = this.getAvailableAssociatedOrganizations()
    }

    removeAssociatedOrganization(org: AssociatedOrganization) {
      this.study.associatedOrganizations =
        this.study.associatedOrganizations.filter(organization => {
          return org.id != organization.id;
        })
      this.availableAssociatedOrganizations = this.getAvailableAssociatedOrganizations();
    }

    addSystemInRole() {
      if (!this.study.systemInRoles) {
          this.study.systemInRoles = []
      }
      const systemInRole = {
        id: null,
        system: null,
        systemRole: null,
      }
      this.study.systemInRoles = [ ...this.study.systemInRoles,systemInRole ]
    }

    private getAllSupplementaryPhysicalSecurityPrinciples(): void {
      this.otherPhysicalSecurityPrinciples = []

      this.securityPrincipleService.getAllSupplementaryPhysicalSecurityPrinciples().subscribe(physicalSecurityPrinciples => {
        physicalSecurityPrinciples.forEach(principle => {
          this.otherPhysicalSecurityPrinciples.push({
            label: this.langPipe.transform((principle.prefLabel)),
            value: principle
          })
        })
      })
    }

    private getAllSupplementaryDigitalSecurityPrinciples(): void {
      this.otherDigitalSecurityPrinciples = []

      this.securityPrincipleService.getAllSupplementaryDigitalSecurityPrinciples().subscribe(digitalSecurityPrinciples => {
        digitalSecurityPrinciples.forEach(principle => {
          this.otherDigitalSecurityPrinciples.push({
            label: this.langPipe.transform((principle.prefLabel)),
            value: principle
          })
        })
        this.otherDigitalSecuritySelect.ngOnInit()
      })
    }

    showAddPhysicalSecurityPrincipleModal(): void {
      this.newSupplementaryPhysicalSecurityPrinciple = this.securityPrincipleService.initNewSupplementaryPhysicalSecurityPrinciple()
    }

    showAddDigitalSecurityPrincipleModal(): void {
      this.newSupplementaryDigitalSecurityPrinciple = this.securityPrincipleService.initNewSupplementaryDigitalSecurityPrinciple()
    }

    savePhysicalSecurityPrinciple(event): void {
      this.securityPrincipleService.saveSupplementarySecurityPrinciple(this.newSupplementaryPhysicalSecurityPrinciple)
        .subscribe(savedSecurityPrinciple => {
          this.getAllSupplementaryPhysicalSecurityPrinciples()
          this.study.otherPrinciplesForPhysicalSecurity.push(savedSecurityPrinciple)
          this.closeAddPhysicalSecurityPrincipleModal()
        })
    }

    saveDigitalSecurityPrinciple(event): void {
      this.securityPrincipleService.saveSupplementarySecurityPrinciple(this.newSupplementaryDigitalSecurityPrinciple)
        .subscribe(savedSecurityPrinciple => {
          this.getAllSupplementaryDigitalSecurityPrinciples()
          this.study.otherPrinciplesForDigitalSecurity.push(savedSecurityPrinciple)
          this.closeAddDigitalSecurityPrincipleModal()
        })
    }

    closeAddPhysicalSecurityPrincipleModal(): void {
      this.newSupplementaryPhysicalSecurityPrinciple = null
    }

    closeAddDigitalSecurityPrincipleModal(): void {
      this.newSupplementaryDigitalSecurityPrinciple = null
    }


  ngAfterContentChecked(): void {
      if (this.studyForm) {
        if (this.studyForm !== this.currentForm) {
          this.currentForm = this.studyForm
          this.currentForm.valueChanges.subscribe(data => this.validate(data))
        }
      }
    }

    private validate(data?: any): void {
      this.formErrors = []

      for (const name in this.currentForm.form.controls) {
        const control: AbstractControl = this.currentForm.form.get(name)
        if (control && control.invalid && (this.savingInProgress || this.savingHasFailed)) {
          for (const errorKey in control.errors) {
            if (!this.formErrors[name]) {
              this.formErrors[name] = []
            }
            this.formErrors[name] = [
              ...this.formErrors[name],
              'errors.form.' + errorKey
            ]
          }
        }
      }
    }

    save() {
        this.savingInProgress = true

        this.validate()

        if (!this.study.containsSensitivePersonalData) {
          this.study.legalBasisForHandlingSensitivePersonalData = []
          this.study.otherLegalBasisForHandlingSensitivePersonalData = null
          this.study.typeOfSensitivePersonalData = []
          this.study.otherTypeOfSensitivePersonalData = null
          this.studyService.initializeProperties(this.study)
        }

        if (!this.study.personRegistry) {
          this.study.registryPolicy = null
          this.study.purposeOfPersonRegistry = null
          this.study.usageOfPersonalInformation = null;
          this.study.personRegistrySources = null
          this.study.personRegisterDataTransfers = null
          this.study.personRegisterDataTransfersOutsideEuOrEea = null
          this.study.legalBasisForHandlingPersonalData = []
          this.study.otherLegalBasisForHandlingPersonalData = null
          this.study.containsSensitivePersonalData = false
          this.study.legalBasisForHandlingSensitivePersonalData = []
          this.study.otherLegalBasisForHandlingSensitivePersonalData = null
          this.study.typeOfSensitivePersonalData = []
          this.study.otherTypeOfSensitivePersonalData = null
          this.studyService.initializeProperties(this.study)
        }

        if (!(this.study.confidentialityClass === ConfidentialityClass.PARTLY_CONFIDENTIAL || ConfidentialityClass.CONFIDENTIAL)) {
          this.study.groundsForConfidentiality = null
          this.study.securityClassification = null
          this.studyService.initializeProperties(this.study)
        }

        if(this.study.otherPrinciplesForPhysicalSecurity.length > 0 &&
          this.study.principlesForPhysicalSecurity.indexOf(PrincipleForPhysicalSecurity.OTHER) == -1) {
          this.study.otherPrinciplesForPhysicalSecurity = []
        }

        if(this.study.otherPrinciplesForDigitalSecurity.length > 0 &&
            this.study.principlesForDigitalSecurity.indexOf(PrincipleForDigitalSecurity.OTHER) == -1) {
            this.study.otherPrinciplesForDigitalSecurity = []
        }

        if (this.currentForm.invalid) {
          this.growlMessageService.buildAndShowMessage('error',
            'operations.common.save.result.fail.summary',
            'operations.common.save.result.fail.detail')
          this.savingInProgress = false
          this.savingHasFailed = true
          return
        }

        this.study.dataProcessingStartDate = this.dataProcessingStartDate ?
          this.dateUtils.convertToIsoDate(this.dataProcessingStartDate) : null
        this.study.dataProcessingEndDate = this.dataProcessingEndDate ?
          this.dateUtils.convertToIsoDate(this.dataProcessingEndDate) : null

        this.studyService.save(this.study)
            .finally(() => {
              this.savingInProgress = false
            })
            .subscribe(savedStudy => {
                this.study = savedStudy;
                this.goBack();
            });
    }

    goBack() {
        if (this.study.id) {
            this.router.navigate(['/editor/studies', this.study.id, 'administrative-information']);
        } else {
            this.router.navigate(['/editor/studies']);
        }
    }

    private onIsScientificStudyChanged(): void {
      this.study.postStudyRetentionOfPersonalData = undefined
      this.updateSelectableRetentionOptions()
    }

    private updateSelectableRetentionOptions(): void {
      this.selectableRetentionOptions = (<any>Object).values(PostStudyRetentionOfPersonalData)
        .filter(option => this.study.isScientificStudy ? /SCIENTIFIC/.test(option) : !/SCIENTIFIC/.test(option))
    }

    private postStudyRetentionDisposeNotSelected(): boolean {
      const selectedValue = this.study.postStudyRetentionOfPersonalData;
      return PostStudyRetentionOfPersonalData.DISPOSE !== selectedValue &&
             PostStudyRetentionOfPersonalData.DISPOSE_SCIENTIFIC !== selectedValue;
  }

}
