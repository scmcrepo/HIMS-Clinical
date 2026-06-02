-- V072__create_casesheet_module.sql
-- Dynamically configurable case sheet module.
-- Tables: case_sheet_templates, case_sheet_template_fields, case_sheet_records
-- Seed: Clinical-grade Orthopaedics OP + IP templates

-- ─────────────────────────────────────────────────────────
-- 1. CASE_SHEET_TEMPLATES
-- ─────────────────────────────────────────────────────────
CREATE TABLE case_sheet_templates (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    name            VARCHAR(120) NOT NULL,
    specialization  VARCHAR(60)  NOT NULL,
    visit_type      VARCHAR(10)  NOT NULL CHECK (visit_type IN ('OP','IP','BOTH')),
    description     TEXT,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_by      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_by     UUID,
    modified_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_cst PRIMARY KEY (id),
    CONSTRAINT uq_cst_name_spec UNIQUE (name, specialization, visit_type)
);
CREATE INDEX idx_cst_specialization ON case_sheet_templates(specialization);
CREATE INDEX idx_cst_visit_type     ON case_sheet_templates(visit_type);

-- ─────────────────────────────────────────────────────────
-- 2. CASE_SHEET_TEMPLATE_FIELDS
-- field_type values: TEXT | TEXTAREA | NUMBER | SELECT | MULTI_SELECT |
--                    DATE | CHECKBOX | RADIO | HEADING | ROM_GRID |
--                    FUNCTIONAL_SCORE | IMPLANT_LOG | PREOP_CHECKLIST
-- ─────────────────────────────────────────────────────────
CREATE TABLE case_sheet_template_fields (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    template_id    UUID         NOT NULL,
    field_key      VARCHAR(80)  NOT NULL,
    label          VARCHAR(120) NOT NULL,
    field_type     VARCHAR(30)  NOT NULL,
    section        VARCHAR(80),
    display_order  INTEGER      NOT NULL DEFAULT 0,
    is_required    BOOLEAN      NOT NULL DEFAULT FALSE,
    placeholder    VARCHAR(200),
    help_text      VARCHAR(300),
    options        JSONB,
    validation     JSONB,
    default_value  VARCHAR(200),
    is_visible     BOOLEAN      NOT NULL DEFAULT TRUE,
    status         SMALLINT     NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_cstf PRIMARY KEY (id),
    CONSTRAINT fk_cstf_template FOREIGN KEY (template_id) REFERENCES case_sheet_templates(id),
    CONSTRAINT uq_cstf_key UNIQUE (template_id, field_key)
);
CREATE INDEX idx_cstf_template ON case_sheet_template_fields(template_id);
CREATE INDEX idx_cstf_order    ON case_sheet_template_fields(template_id, display_order);

-- ─────────────────────────────────────────────────────────
-- 3. CASE_SHEET_RECORDS
-- ─────────────────────────────────────────────────────────
CREATE TABLE case_sheet_records (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    encounter_id UUID        NOT NULL,
    template_id  UUID        NOT NULL,
    data         JSONB       NOT NULL DEFAULT '{}',
    recorded_by  UUID,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status       SMALLINT    NOT NULL DEFAULT 1,
    created_by   UUID,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modified_by  UUID,
    modified_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_csr           PRIMARY KEY (id),
    CONSTRAINT fk_csr_encounter FOREIGN KEY (encounter_id) REFERENCES clinical_encounters(id),
    CONSTRAINT fk_csr_template  FOREIGN KEY (template_id)  REFERENCES case_sheet_templates(id),
    CONSTRAINT uq_csr_enc_tmpl  UNIQUE (encounter_id, template_id)
);
CREATE INDEX idx_csr_encounter ON case_sheet_records(encounter_id);
CREATE INDEX idx_csr_template  ON case_sheet_records(template_id);
CREATE INDEX idx_csr_data_gin  ON case_sheet_records USING gin(data);

-- ─────────────────────────────────────────────────────────
-- 4. SEED: CLINICAL-GRADE ORTHOPAEDICS OP + IP TEMPLATES
-- ─────────────────────────────────────────────────────────
DO $$
DECLARE
  v_op UUID := gen_random_uuid();
  v_ip UUID := gen_random_uuid();
BEGIN

-- ══════════════════════════════════════════════════════════
-- ORTHOPAEDICS OP TEMPLATE
-- ══════════════════════════════════════════════════════════
INSERT INTO case_sheet_templates(id,name,specialization,visit_type,description,is_default)
VALUES(v_op,'Orthopaedics OP Default','ORTHOPAEDICS','OP',
  'Clinical-grade outpatient case sheet — Orthopaedics',TRUE);

INSERT INTO case_sheet_template_fields
  (template_id,field_key,label,field_type,section,display_order,is_required,placeholder,help_text,options,validation)
VALUES

-- ── SECTION: Presenting Complaint ──────────────────────
(v_op,'s1','Presenting Complaint','HEADING','Presenting Complaint',10,FALSE,NULL,NULL,NULL,NULL),
(v_op,'chief_complaint','Chief Complaint','TEXTAREA','Presenting Complaint',20,TRUE,
  'Describe the primary complaint…',NULL,NULL,NULL),
(v_op,'pain_site','Site of Pain','SELECT','Presenting Complaint',30,TRUE,NULL,NULL,
  '[{"value":"cervical","label":"Cervical Spine"},{"value":"thoracic","label":"Thoracic Spine"},
    {"value":"lumbar","label":"Lumbar Spine"},{"value":"shoulder_r","label":"Right Shoulder"},
    {"value":"shoulder_l","label":"Left Shoulder"},{"value":"elbow_r","label":"Right Elbow"},
    {"value":"elbow_l","label":"Left Elbow"},{"value":"wrist_r","label":"Right Wrist"},
    {"value":"wrist_l","label":"Left Wrist"},{"value":"hip_r","label":"Right Hip"},
    {"value":"hip_l","label":"Left Hip"},{"value":"knee_r","label":"Right Knee"},
    {"value":"knee_l","label":"Left Knee"},{"value":"ankle_r","label":"Right Ankle"},
    {"value":"ankle_l","label":"Left Ankle"},{"value":"foot_r","label":"Right Foot"},
    {"value":"foot_l","label":"Left Foot"},{"value":"other","label":"Other"}]'::jsonb,NULL),
(v_op,'pain_onset','Onset','SELECT','Presenting Complaint',40,FALSE,NULL,NULL,
  '[{"value":"acute","label":"Acute (< 2 weeks)"},{"value":"subacute","label":"Sub-acute (2–6 weeks)"},
    {"value":"chronic","label":"Chronic (> 6 weeks)"},{"value":"insidious","label":"Insidious onset"}]'::jsonb,NULL),
(v_op,'pain_duration','Duration of Symptoms','TEXT','Presenting Complaint',50,FALSE,'e.g. 3 weeks',NULL,NULL,NULL),
(v_op,'pain_character','Character of Pain','MULTI_SELECT','Presenting Complaint',60,FALSE,NULL,NULL,
  '[{"value":"sharp","label":"Sharp"},{"value":"dull","label":"Dull Aching"},
    {"value":"burning","label":"Burning"},{"value":"throbbing","label":"Throbbing"},
    {"value":"radiating","label":"Radiating"},{"value":"constant","label":"Constant"},
    {"value":"intermittent","label":"Intermittent"},{"value":"nocturnal","label":"Night pain"}]'::jsonb,NULL),
(v_op,'pain_score','Pain Score (VAS 0–10)','NUMBER','Presenting Complaint',70,TRUE,
  '0 = no pain, 10 = worst imaginable','Visual Analogue Scale',NULL,'{"min":0,"max":10}'::jsonb),
(v_op,'aggravating','Aggravating Factors','TEXTAREA','Presenting Complaint',80,FALSE,NULL,NULL,NULL,NULL),
(v_op,'relieving','Relieving Factors','TEXTAREA','Presenting Complaint',90,FALSE,NULL,NULL,NULL,NULL),
(v_op,'functional_limitation','Functional Limitation','MULTI_SELECT','Presenting Complaint',95,FALSE,NULL,NULL,
  '[{"value":"walking","label":"Walking"},{"value":"climbing_stairs","label":"Climbing Stairs"},
    {"value":"sitting_standing","label":"Sitting / Standing"},{"value":"dressing","label":"Dressing"},
    {"value":"driving","label":"Driving"},{"value":"sleep","label":"Sleep disturbance"},
    {"value":"work","label":"Unable to work"},{"value":"sports","label":"Sports / Exercise"}]'::jsonb,NULL),

-- ── SECTION: History ────────────────────────────────────
(v_op,'s2','History','HEADING','History',100,FALSE,NULL,NULL,NULL,NULL),
(v_op,'hopi','History of Present Illness','TEXTAREA','History',110,FALSE,NULL,NULL,NULL,NULL),
(v_op,'trauma_history','Trauma / Injury History','TEXTAREA','History',120,FALSE,
  'Mechanism, date, initial treatment…',NULL,NULL,NULL),
(v_op,'past_surgeries','Previous Surgeries / Procedures','TEXTAREA','History',130,FALSE,
  'Dates, procedures, outcomes…',NULL,NULL,NULL),
(v_op,'comorbidities','Comorbidities','MULTI_SELECT','History',140,FALSE,NULL,NULL,
  '[{"value":"dm","label":"Diabetes Mellitus"},{"value":"htn","label":"Hypertension"},
    {"value":"ckd","label":"Chronic Kidney Disease"},{"value":"ihd","label":"Ischaemic Heart Disease"},
    {"value":"osteoporosis","label":"Osteoporosis"},{"value":"ra","label":"Rheumatoid Arthritis"},
    {"value":"gout","label":"Gout / Pseudogout"},{"value":"obesity","label":"Obesity (BMI > 30)"},
    {"value":"pvd","label":"Peripheral Vascular Disease"},{"value":"none","label":"None"}]'::jsonb,NULL),
(v_op,'drug_history','Drug History / Current Medications','TEXTAREA','History',150,FALSE,
  'Include NSAIDs, steroids, anticoagulants, DMARDs…',NULL,NULL,NULL),
(v_op,'allergy','Allergies','TEXT','History',160,FALSE,'Drug / latex / metal allergies…',NULL,NULL,NULL),
(v_op,'occupation','Occupation','TEXT','History',165,FALSE,'e.g. manual labourer, office worker',NULL,NULL,NULL),
(v_op,'dominant_hand','Dominant Hand','RADIO','History',167,FALSE,NULL,NULL,
  '[{"value":"right","label":"Right"},{"value":"left","label":"Left"},{"value":"ambidextrous","label":"Ambidextrous"}]'::jsonb,NULL),

-- ── SECTION: Clinical Examination ──────────────────────
(v_op,'s3','Clinical Examination','HEADING','Clinical Examination',170,FALSE,NULL,NULL,NULL,NULL),
(v_op,'gait','Gait','SELECT','Clinical Examination',175,FALSE,NULL,NULL,
  '[{"value":"normal","label":"Normal"},{"value":"antalgic","label":"Antalgic"},
    {"value":"trendelenburg","label":"Trendelenburg"},{"value":"steppage","label":"Steppage"},
    {"value":"waddling","label":"Waddling"},{"value":"spastic","label":"Spastic"},
    {"value":"unable","label":"Unable to walk"}]'::jsonb,NULL),
(v_op,'limb_alignment','Limb Alignment / Deformity','MULTI_SELECT','Clinical Examination',177,FALSE,NULL,NULL,
  '[{"value":"varus","label":"Varus"},{"value":"valgus","label":"Valgus"},
    {"value":"flexion","label":"Flexion deformity"},{"value":"hyperextension","label":"Hyperextension"},
    {"value":"shortening","label":"Limb shortening"},{"value":"rotation","label":"Rotational deformity"},
    {"value":"none","label":"None"}]'::jsonb,NULL),
(v_op,'lld','Limb Length Discrepancy','TEXT','Clinical Examination',178,FALSE,'e.g. 2 cm short on right',NULL,NULL,NULL),
(v_op,'swelling','Swelling','RADIO','Clinical Examination',180,FALSE,NULL,NULL,
  '[{"value":"none","label":"None"},{"value":"mild","label":"Mild"},
    {"value":"moderate","label":"Moderate"},{"value":"severe","label":"Severe"}]'::jsonb,NULL),
(v_op,'local_temperature','Local Temperature','RADIO','Clinical Examination',182,FALSE,NULL,NULL,
  '[{"value":"normal","label":"Normal"},{"value":"warm","label":"Warm / Hot"},{"value":"cold","label":"Cold"}]'::jsonb,NULL),
(v_op,'tenderness','Tenderness','TEXTAREA','Clinical Examination',185,FALSE,
  'Site, degree (grade 1–4), bony / soft tissue…',NULL,NULL,NULL),
(v_op,'rom','Range of Motion','ROM_GRID','Clinical Examination',190,FALSE,NULL,
  'Record active range in degrees. Leave blank if not applicable.',NULL,NULL),
(v_op,'muscle_power','Muscle Power (MRC Grade 0–5)','TEXTAREA','Clinical Examination',200,FALSE,
  'e.g. Quadriceps 4/5 bilateral; Hip abductors 3/5 right',NULL,NULL,NULL),
(v_op,'special_tests','Special Tests','TEXTAREA','Clinical Examination',210,FALSE,
  'e.g. Lachman +ve, McMurray +ve medial, Anterior drawer –ve',NULL,NULL,NULL),
(v_op,'neurovascular','Neurovascular Assessment','TEXTAREA','Clinical Examination',220,FALSE,
  'Sensation (L1–S2 dermatomes), motor, pulses (DP/PT), capillary refill',NULL,NULL,NULL),

-- ── SECTION: Investigations ─────────────────────────────
(v_op,'s4','Investigations','HEADING','Investigations',230,FALSE,NULL,NULL,NULL,NULL),
(v_op,'xray_findings','X-Ray Findings','TEXTAREA','Investigations',240,FALSE,
  'Views, joint space, alignment, bone density, loose bodies…',NULL,NULL,NULL),
(v_op,'mri_findings','MRI Findings','TEXTAREA','Investigations',250,FALSE,NULL,NULL,NULL,NULL),
(v_op,'ct_findings','CT Scan Findings','TEXTAREA','Investigations',255,FALSE,NULL,NULL,NULL,NULL),
(v_op,'fracture_classification','Fracture Classification','TEXT','Investigations',258,FALSE,
  'e.g. AO 41-A2, Garden II, Neer 2-part, Schatzker IV',
  'Use standard classification (AO/OTA, Garden, Neer, Schatzker etc.)',NULL,NULL),
(v_op,'lab_findings','Lab / Blood Findings','TEXTAREA','Investigations',260,FALSE,
  'CBC, CRP, ESR, uric acid, calcium, ALP, Vit D…',NULL,NULL,NULL),

-- ── SECTION: Functional Scores ──────────────────────────
(v_op,'s5','Functional Outcome Scores','HEADING','Functional Scores',270,FALSE,NULL,NULL,NULL,NULL),
(v_op,'functional_scores','Functional Scores','FUNCTIONAL_SCORE','Functional Scores',280,FALSE,NULL,
  'Document validated functional scores. Score values are used for outcome tracking.',NULL,NULL),

-- ── SECTION: Assessment & Plan ──────────────────────────
(v_op,'s6','Assessment & Plan','HEADING','Assessment & Plan',290,FALSE,NULL,NULL,NULL,NULL),
(v_op,'diagnosis','Provisional / Final Diagnosis','TEXTAREA','Assessment & Plan',300,TRUE,NULL,NULL,NULL,NULL),
(v_op,'icd_code','ICD-10 Code','TEXT','Assessment & Plan',305,FALSE,'e.g. M17.1 Primary OA of knee',NULL,NULL,NULL),
(v_op,'management_type','Management Type','RADIO','Assessment & Plan',310,TRUE,NULL,NULL,
  '[{"value":"conservative","label":"Conservative"},{"value":"surgical","label":"Surgical"},
    {"value":"observation","label":"Observation / Follow-up"},
    {"value":"refer","label":"Refer to tertiary centre"}]'::jsonb,NULL),
(v_op,'treatment_plan','Treatment Plan','TEXTAREA','Assessment & Plan',320,FALSE,NULL,NULL,NULL,NULL),
(v_op,'physiotherapy','Physiotherapy Advice','TEXTAREA','Assessment & Plan',330,FALSE,
  'Type, frequency, weight-bearing status, exercises…',NULL,NULL,NULL),
(v_op,'weight_bearing_status','Weight-Bearing Status','SELECT','Assessment & Plan',335,FALSE,NULL,NULL,
  '[{"value":"fwb","label":"Full Weight Bearing"},{"value":"pwb","label":"Partial Weight Bearing"},
    {"value":"ttwb","label":"Toe-Touch Weight Bearing"},{"value":"nwb","label":"Non Weight Bearing"},
    {"value":"na","label":"Not Applicable"}]'::jsonb,NULL),
(v_op,'follow_up_date','Follow-up Date','DATE','Assessment & Plan',340,FALSE,NULL,NULL,NULL,NULL),
(v_op,'follow_up_xray','Follow-up X-Ray Required','RADIO','Assessment & Plan',345,FALSE,NULL,NULL,
  '[{"value":"yes","label":"Yes"},{"value":"no","label":"No"}]'::jsonb,NULL),
(v_op,'advice','Advice to Patient','TEXTAREA','Assessment & Plan',350,FALSE,NULL,NULL,NULL,NULL);

-- ══════════════════════════════════════════════════════════
-- ORTHOPAEDICS IP TEMPLATE
-- ══════════════════════════════════════════════════════════
INSERT INTO case_sheet_templates(id,name,specialization,visit_type,description,is_default)
VALUES(v_ip,'Orthopaedics IP Default','ORTHOPAEDICS','IP',
  'Clinical-grade inpatient case sheet — Orthopaedics',TRUE);

INSERT INTO case_sheet_template_fields
  (template_id,field_key,label,field_type,section,display_order,is_required,placeholder,help_text,options,validation)
VALUES

-- ── SECTION: Admission ─────────────────────────────────
(v_ip,'s1','Admission Details','HEADING','Admission Details',5,FALSE,NULL,NULL,NULL,NULL),
(v_ip,'admission_reason','Reason for Admission','TEXTAREA','Admission Details',10,TRUE,NULL,NULL,NULL,NULL),
(v_ip,'presenting_diagnosis','Presenting Diagnosis','TEXT','Admission Details',12,TRUE,
  'e.g. Right NOF fracture Garden III',NULL,NULL,NULL),
(v_ip,'fracture_classification','Fracture / Injury Classification','TEXT','Admission Details',14,FALSE,
  'e.g. AO 41-A2, Garden III, Neer 3-part',
  'Use standard classification (AO/OTA, Garden, Neer, Schatzker, Denis etc.)',NULL,NULL),

-- ── SECTION: Presenting Complaint ──────────────────────
(v_ip,'chief_complaint','Chief Complaint','TEXTAREA','Presenting Complaint',20,TRUE,NULL,NULL,NULL,NULL),
(v_ip,'pain_site','Site of Pain','SELECT','Presenting Complaint',25,FALSE,NULL,NULL,
  '[{"value":"cervical","label":"Cervical Spine"},{"value":"lumbar","label":"Lumbar Spine"},
    {"value":"shoulder_r","label":"Right Shoulder"},{"value":"shoulder_l","label":"Left Shoulder"},
    {"value":"hip_r","label":"Right Hip"},{"value":"hip_l","label":"Left Hip"},
    {"value":"knee_r","label":"Right Knee"},{"value":"knee_l","label":"Left Knee"},
    {"value":"femur_r","label":"Right Femur"},{"value":"femur_l","label":"Left Femur"},
    {"value":"tibia_r","label":"Right Tibia"},{"value":"tibia_l","label":"Left Tibia"},
    {"value":"spine","label":"Spine (multilevel)"},{"value":"other","label":"Other"}]'::jsonb,NULL),
(v_ip,'pain_score','Pain Score (VAS 0–10)','NUMBER','Presenting Complaint',28,TRUE,
  '0 = no pain, 10 = worst','Visual Analogue Scale',NULL,'{"min":0,"max":10}'::jsonb),
(v_ip,'hopi','History of Present Illness','TEXTAREA','History',30,TRUE,NULL,NULL,NULL,NULL),
(v_ip,'trauma_history','Trauma / Mechanism of Injury','TEXTAREA','History',35,FALSE,
  'Mechanism, date, time, force applied, associated injuries…',NULL,NULL,NULL),
(v_ip,'past_surgeries','Previous Surgeries / Implants','TEXTAREA','History',40,FALSE,
  'Previous metalwork in situ, prior arthroplasty…',NULL,NULL,NULL),
(v_ip,'comorbidities','Comorbidities','MULTI_SELECT','History',45,FALSE,NULL,NULL,
  '[{"value":"dm","label":"Diabetes Mellitus"},{"value":"htn","label":"Hypertension"},
    {"value":"osteoporosis","label":"Osteoporosis"},{"value":"ra","label":"Rheumatoid Arthritis"},
    {"value":"ckd","label":"Chronic Kidney Disease"},{"value":"ihd","label":"Ischaemic Heart Disease"},
    {"value":"pvd","label":"Peripheral Vascular Disease"},{"value":"anticoagulants","label":"On Anticoagulants"},
    {"value":"steroids","label":"Long-term Steroids"},{"value":"none","label":"None"}]'::jsonb,NULL),
(v_ip,'drug_history','Current Medications','TEXTAREA','History',50,FALSE,
  'Include anticoagulants, antiplatelets, DMARDs, steroids…',NULL,NULL,NULL),
(v_ip,'allergy','Allergies','TEXT','History',55,FALSE,
  'Drug / latex / metal (nickel/cobalt/chromium) allergies…',NULL,NULL,NULL),

-- ── SECTION: Examination ───────────────────────────────
(v_ip,'gait','Gait / Mobility Pre-op','SELECT','Clinical Examination',60,FALSE,NULL,NULL,
  '[{"value":"normal","label":"Normal"},{"value":"antalgic","label":"Antalgic"},
    {"value":"trendelenburg","label":"Trendelenburg"},{"value":"unable","label":"Unable to walk"},
    {"value":"bedridden","label":"Bedridden prior to admission"}]'::jsonb,NULL),
(v_ip,'limb_alignment','Deformity / Alignment','MULTI_SELECT','Clinical Examination',62,FALSE,NULL,NULL,
  '[{"value":"varus","label":"Varus"},{"value":"valgus","label":"Valgus"},
    {"value":"flexion","label":"Flexion deformity"},{"value":"shortening","label":"Shortening"},
    {"value":"rotation","label":"Rotational deformity"},{"value":"none","label":"None"}]'::jsonb,NULL),
(v_ip,'lld','Limb Length Discrepancy','TEXT','Clinical Examination',64,FALSE,'e.g. 3 cm short right',NULL,NULL,NULL),
(v_ip,'swelling','Swelling / Bruising','TEXTAREA','Clinical Examination',66,FALSE,
  'Site, extent, ecchymosis…',NULL,NULL,NULL),
(v_ip,'rom','Range of Motion','ROM_GRID','Clinical Examination',68,FALSE,NULL,
  'Record pre-operative active range in degrees.',NULL,NULL),
(v_ip,'muscle_power','Muscle Power (MRC 0–5)','TEXTAREA','Clinical Examination',70,FALSE,
  'Distal motor function — required for all cases with neurovascular risk',NULL,NULL,NULL),
(v_ip,'neurovascular_status','Neurovascular Assessment','TEXTAREA','Clinical Examination',72,TRUE,
  'Sensation (dermatomal), motor (key muscles), pulses (DP, PT), capillary refill, compartment pressures if indicated',
  'MUST document pre-operatively for medico-legal purposes',NULL,NULL),
(v_ip,'special_tests','Special Tests / Stability','TEXTAREA','Clinical Examination',74,FALSE,
  'Ligament stability, provocative tests, impingement signs…',NULL,NULL,NULL),

-- ── SECTION: Investigations ─────────────────────────────
(v_ip,'xray_findings','X-Ray Findings','TEXTAREA','Investigations',80,TRUE,
  'Views obtained, fracture pattern, displacement, cortical integrity…',NULL,NULL,NULL),
(v_ip,'mri_findings','MRI Findings','TEXTAREA','Investigations',85,FALSE,NULL,NULL,NULL,NULL),
(v_ip,'ct_findings','CT Scan Findings','TEXTAREA','Investigations',87,FALSE,
  'Comminution, intra-articular extension, bone stock…',NULL,NULL,NULL),
(v_ip,'lab_pre_op','Pre-op Labs','TEXTAREA','Investigations',90,TRUE,
  'Hb, WBC, Platelets, Creatinine, Electrolytes, PT/INR, Blood group & cross-match',NULL,NULL,NULL),
(v_ip,'ecg_echo','ECG / Echo / Anaesthesia Fitness','TEXTAREA','Investigations',92,FALSE,NULL,NULL,NULL,NULL),

-- ── SECTION: Functional Score (pre-op baseline) ─────────
(v_ip,'functional_scores','Pre-operative Functional Scores','FUNCTIONAL_SCORE','Functional Scores',95,FALSE,NULL,
  'Record baseline score for outcome comparison at follow-up.',NULL,NULL),

-- ── SECTION: Pre-operative Checklist ───────────────────
(v_ip,'preop_checklist','Pre-operative Checklist','PREOP_CHECKLIST','Pre-operative Checklist',100,TRUE,NULL,
  'All items must be confirmed before patient goes to theatre.',NULL,NULL),

-- ── SECTION: Surgical Details ───────────────────────────
(v_ip,'s_surg','Surgical Details','HEADING','Surgical Details',110,FALSE,NULL,NULL,NULL,NULL),
(v_ip,'planned_surgery','Planned Procedure','TEXTAREA','Surgical Details',115,TRUE,
  'e.g. Right Total Knee Replacement — cemented, PS design',NULL,NULL,NULL),
(v_ip,'anaesthesia_type','Anaesthesia Type','SELECT','Surgical Details',120,FALSE,NULL,NULL,
  '[{"value":"ga","label":"General Anaesthesia"},{"value":"sa","label":"Spinal Anaesthesia"},
    {"value":"cse","label":"Combined Spinal-Epidural"},{"value":"ra","label":"Regional Block"},
    {"value":"la","label":"Local Anaesthesia"}]'::jsonb,NULL),
(v_ip,'surgical_approach','Surgical Approach','TEXT','Surgical Details',125,FALSE,
  'e.g. Posterior approach hip, Medial parapatellar knee',NULL,NULL,NULL),
(v_ip,'tourniquet','Tourniquet Used','RADIO','Surgical Details',127,FALSE,NULL,NULL,
  '[{"value":"yes","label":"Yes"},{"value":"no","label":"No"}]'::jsonb,NULL),
(v_ip,'tourniquet_time','Tourniquet Time (minutes)','NUMBER','Surgical Details',128,FALSE,NULL,NULL,NULL,
  '{"min":0,"max":240}'::jsonb),
(v_ip,'implant_log','Implant Log','IMPLANT_LOG','Surgical Details',130,FALSE,NULL,
  'Record each implant component with manufacturer, batch/lot and size.',NULL,NULL),
(v_ip,'intra_op_notes','Intra-operative Notes','TEXTAREA','Surgical Details',140,FALSE,
  'Findings, technique details, bone quality, complications intra-op…',NULL,NULL,NULL),
(v_ip,'blood_loss','Estimated Blood Loss (mL)','NUMBER','Surgical Details',145,FALSE,NULL,NULL,NULL,
  '{"min":0,"max":10000}'::jsonb),
(v_ip,'transfusion','Blood Transfusion Given','RADIO','Surgical Details',148,FALSE,NULL,NULL,
  '[{"value":"none","label":"None"},{"value":"intra","label":"Intra-operative"},
    {"value":"post","label":"Post-operative"},{"value":"cell_saver","label":"Cell Saver used"}]'::jsonb,NULL),
(v_ip,'drains','Drains Inserted','TEXT','Surgical Details',149,FALSE,
  'Type, number, suction / free drainage',NULL,NULL,NULL),

-- ── SECTION: Post-operative / Progress ─────────────────
(v_ip,'s_postop','Post-operative / Progress Notes','HEADING','Post-operative Progress',150,FALSE,NULL,NULL,NULL,NULL),
(v_ip,'post_op_day','Post-op Day','NUMBER','Post-operative Progress',155,FALSE,NULL,NULL,NULL,
  '{"min":0,"max":90}'::jsonb),
(v_ip,'post_op_pain_score','Post-op Pain Score (VAS)','NUMBER','Post-operative Progress',157,FALSE,
  '0–10',NULL,NULL,'{"min":0,"max":10}'::jsonb),
(v_ip,'wound_status','Wound Status','SELECT','Post-operative Progress',160,FALSE,NULL,NULL,
  '[{"value":"clean_dry","label":"Clean / Dry"},{"value":"healing","label":"Healing Well"},
    {"value":"serous","label":"Serous Discharge"},{"value":"haematoma","label":"Wound Haematoma"},
    {"value":"infected","label":"Signs of Infection"},{"value":"dehiscence","label":"Wound Dehiscence"}]'::jsonb,NULL),
(v_ip,'drain_output','Drain Output (mL)','NUMBER','Post-operative Progress',162,FALSE,NULL,NULL,NULL,
  '{"min":0}'::jsonb),
(v_ip,'post_op_xray','Post-op X-Ray Findings','TEXTAREA','Post-operative Progress',164,FALSE,
  'Implant position, alignment, cement mantle…',NULL,NULL,NULL),
(v_ip,'weight_bearing_status','Weight-Bearing Status','SELECT','Post-operative Progress',166,FALSE,NULL,NULL,
  '[{"value":"fwb","label":"Full Weight Bearing"},{"value":"pwb","label":"Partial Weight Bearing (50%)"},
    {"value":"ttwb","label":"Toe-Touch Weight Bearing"},{"value":"nwb","label":"Non Weight Bearing"}]'::jsonb,NULL),
(v_ip,'dvt_prophylaxis','DVT Prophylaxis Given','RADIO','Post-operative Progress',168,FALSE,NULL,
  'Mechanical (TED stockings / IPC) and/or chemical (LMWH)',
  '[{"value":"mechanical","label":"Mechanical only"},{"value":"chemical","label":"Chemical (LMWH)"},
    {"value":"both","label":"Both"},{"value":"none","label":"None / Contraindicated"}]'::jsonb,NULL),
(v_ip,'post_op_physio','Post-op Physiotherapy Protocol','TEXTAREA','Post-operative Progress',170,FALSE,
  'Exercises, mobilisation goals, aids (frame / crutches / stick)…',NULL,NULL,NULL),
(v_ip,'complications','Complications','MULTI_SELECT','Post-operative Progress',175,FALSE,NULL,NULL,
  '[{"value":"dvt","label":"DVT"},{"value":"pe","label":"Pulmonary Embolism"},
    {"value":"infection","label":"Surgical Site Infection"},{"value":"dislocation","label":"Dislocation"},
    {"value":"nerve","label":"Nerve Injury"},{"value":"vessel","label":"Vascular Injury"},
    {"value":"implant_fail","label":"Implant Failure"},{"value":"refracture","label":"Peri-prosthetic Fracture"},
    {"value":"none","label":"None"}]'::jsonb,NULL),

-- ── SECTION: Discharge Plan ─────────────────────────────
(v_ip,'s_dc','Discharge Plan','HEADING','Discharge Plan',180,FALSE,NULL,NULL,NULL,NULL),
(v_ip,'discharge_plan','Discharge Plan','TEXTAREA','Discharge Plan',185,FALSE,
  'Home / rehab facility, social support, equipment needed…',NULL,NULL,NULL),
(v_ip,'discharge_medications','Medications on Discharge','TEXTAREA','Discharge Plan',188,FALSE,
  'Include analgesics, antibiotics, LMWH duration, VTE prophylaxis…',NULL,NULL,NULL),
(v_ip,'wound_care_instructions','Wound Care Instructions','TEXTAREA','Discharge Plan',190,FALSE,
  'Dressing changes, suture/staple removal date…',NULL,NULL,NULL),
(v_ip,'follow_up_date','First Follow-up Date','DATE','Discharge Plan',192,FALSE,NULL,NULL,NULL,NULL),
(v_ip,'follow_up_instructions','Follow-up Instructions','TEXTAREA','Discharge Plan',194,FALSE,
  'X-ray required at follow-up, physiotherapy continuation, weight-bearing progression…',NULL,NULL,NULL),
(v_ip,'red_flags','Red Flags — Return to ED if','TEXTAREA','Discharge Plan',196,FALSE,
  'Increased swelling, redness, fever, wound breakdown, neurological changes, chest pain…',NULL,NULL,NULL);

END $$;
