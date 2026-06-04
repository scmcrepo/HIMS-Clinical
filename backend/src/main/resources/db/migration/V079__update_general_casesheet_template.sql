-- V077__update_general_casesheet_template.sql
-- Update the GENERAL template to match the requested layout (removed VitalSigns)

UPDATE template
SET template = $tmpl$div(ng-if="validatePermission('tForm.chiefComplaints')")
  h4(class='heading1') Chief Complaints
  div(class='row form-group')
    div(class='col-lg-offset-1 col-lg-8')
      textbox(ng-model='tForm.chiefComplaints', rows='2')
div(ng-if="validatePermission('tForm.medHistory')",style="margin:0px",class='row')
  h4(class='heading1') Medical History
  div(class='row form-group', ng-if='validatePermission("tForm.medHistory.previousHistory")')
    label(class=' col-lg-2 col-md-2 col-sm-2 col-xs-2 control-label') Previous History
    div(class='col-lg-7 col-md-7 col-sm-7 col-xs-7')
      textbox(ng-model='tForm.medHistory.previousHistory', rows='2')
  div(class='row form-group', ng-if='validatePermission("tForm.medHistory.presentHistory")')
    label(class=' col-lg-2 col-md-2 col-sm-2 col-xs-2 control-label') Present History
    div(class='col-lg-7 col-md-7 col-sm-7 col-xs-7')
      textbox(ng-model='tForm.medHistory.presentHistory', rows='2')
  div(class='row form-group', ng-if='validatePermission("tForm.medHistory.presentMedication")')
    label(class=' col-lg-2 col-md-2 col-sm-2 col-xs-2 control-label') Present Medication
    div(class='col-lg-7 col-md-7 col-sm-7 col-xs-7')
      textbox(ng-model='tForm.medHistory.presentMedication', rows='2')
  div(class='row form-group', ng-if='validatePermission("tForm.medHistory.drugAllergy")')
    label(class=' col-lg-2 col-md-2 col-sm-2 col-xs-2 control-label') Drug Allergy
    div(class='col-lg-7 col-md-7 col-sm-7 col-xs-7')
      textbox(ng-model='tForm.medHistory.drugAllergy', rows='2')
div(ng-if="validatePermission('tForm.clinicalFindings')",style="margin:0px",class='row')
  h4(class='heading1') Clinical Findings
  div(class='row form-group', ng-if='validatePermission("tForm.clinicalFindings.examinationFindings")')
    label(class=' col-lg-2 col-md-2 col-sm-2 col-xs-2 control-label') Examination Findings
    div(class='col-lg-7 col-md-7 col-sm-7 col-xs-7')
      textbox(ng-model='tForm.clinicalFindings.examinationFindings', rows='2')
  div(class='row form-group', ng-if='validatePermission("tForm.clinicalFindings.diagnosis")')
    label(class=' col-lg-2 col-md-2 col-sm-2 col-xs-2 control-label') Diagnosis
    div(class='col-lg-7 col-md-7 col-sm-7 col-xs-7')
      textbox(ng-model='tForm.clinicalFindings.diagnosis', rows='2')
  div(class='row form-group', ng-if='validatePermission("tForm.clinicalFindings.advice")')
    label(class=' col-lg-2 col-md-2 col-sm-2 col-xs-2 control-label') Advice
    div(class='col-lg-7 col-md-7 col-sm-7 col-xs-7')
      textbox(ng-model='tForm.clinicalFindings.advice', rows='2')
  div(class='row form-group', ng-if='validatePermission("tForm.clinicalFindings.counselling")')
    label(class=' col-lg-2 col-md-2 col-sm-2 col-xs-2 control-label') Counselling
    div(class='col-lg-7 col-md-7 col-sm-7 col-xs-7')
      textbox(ng-model='tForm.clinicalFindings.counselling', rows='2')
  div(class='row form-group', ng-if='validatePermission("tForm.clinicalFindings.review")')
    label(class=' col-lg-2 col-md-2 col-sm-2 col-xs-2 control-label') Review
    div(class='col-lg-7 col-md-7 col-sm-7 col-xs-7')
      textbox(ng-model='tForm.clinicalFindings.review', rows='2')$tmpl$,
    templatedata = $tdata${}$tdata$,
    modified_at = NOW()
WHERE templatename = 'GENERAL';
