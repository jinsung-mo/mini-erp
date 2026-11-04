import { type FormEvent, useState } from 'react'
import { PARTNER_ROLES, type Partner, type PartnerInput, type PartnerRole, partnerApi } from '../../api/partners'
import { Field, FieldSet } from '../../components/Field'
import { formatBusinessNo } from '../../format'
import { ActivationButton, FormPanel } from './MasterParts'
import { useMasterForm } from './useMasterForm'

type Props = {
  /** null이면 신규 등록 */
  partner: Partner | null
  notice: string | null
  onSaved: (partner: Partner, message: string) => void
  onClose: () => void
}

type Values = PartnerInput & { code: string }

export function PartnerForm({ partner, notice, onSaved, onClose }: Props) {
  const [values, setValues] = useState<Values>({
    code: partner?.code ?? '',
    name: partner?.name ?? '',
    businessNo: partner?.businessNo ? formatBusinessNo(partner.businessNo) : '',
    roles: partner?.roles ?? [],
  })
  // 중복 코드는 코드 입력 아래, 중복 사업자등록번호는 사업자등록번호 입력 아래에 보여 준다
  const form = useMasterForm(onSaved, { DUPLICATE_CODE: 'code', DUPLICATE_BUSINESS_NO: 'businessNo' })

  function set<K extends keyof Values>(key: K, value: Values[K]) {
    setValues((prev) => ({ ...prev, [key]: value }))
  }

  function toggleRole(role: PartnerRole, checked: boolean) {
    setValues((prev) => ({
      ...prev,
      // 순서는 PARTNER_ROLES(공급처, 고객)를 따른다
      roles: PARTNER_ROLES.map((r) => r.value).filter((r) => (r === role ? checked : prev.roles.includes(r))),
    }))
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const input: PartnerInput = { name: values.name, businessNo: values.businessNo, roles: values.roles }
    if (partner === null) {
      void form.run(() => partnerApi.create({ ...input, code: values.code }), '등록했습니다.')
    } else {
      void form.run(() => partnerApi.update(partner.id, input), '저장했습니다.')
    }
  }

  return (
    <FormPanel
      record={partner}
      title={partner ? '거래처 수정' : '거래처 등록'}
      notice={notice}
      error={form.generalError}
      onClose={onClose}
    >
      <form onSubmit={handleSubmit} noValidate>
        <Field
          label="거래처 코드"
          error={form.fieldError('code')}
          hint={
            partner
              ? '코드는 바꿀 수 없습니다.'
              : '영문 대문자·숫자·하이픈(-) 2~20자. 공급처·고객 같은 바뀔 수 있는 의미는 넣지 않습니다.'
          }
        >
          <input
            value={values.code}
            onChange={(e) => set('code', e.target.value.toUpperCase())}
            disabled={partner !== null}
            maxLength={20}
            placeholder="예: P0001"
            autoFocus={partner === null}
          />
        </Field>
        <Field label="거래처명" error={form.fieldError('name')}>
          <input value={values.name} onChange={(e) => set('name', e.target.value)} maxLength={100} />
        </Field>
        <Field
          label="사업자등록번호"
          error={form.fieldError('businessNo')}
          hint="숫자 10자리. 하이픈은 있어도 됩니다. 개인 고객·해외 거래처는 비워 둡니다."
        >
          <input
            value={values.businessNo}
            onChange={(e) => set('businessNo', e.target.value)}
            inputMode="numeric"
            maxLength={12}
            placeholder="예: 123-45-67891"
          />
        </Field>
        <FieldSet
          legend="구분"
          error={form.fieldError('roles')}
          hint="공급처이면서 고객인 회사는 둘 다 선택합니다. 같은 회사를 두 번 등록하지 않습니다."
        >
          {PARTNER_ROLES.map((role) => (
            <label key={role.value} className="choice">
              <input
                type="checkbox"
                name="roles"
                value={role.value}
                checked={values.roles.includes(role.value)}
                onChange={(e) => toggleRole(role.value, e.target.checked)}
              />
              {role.label}
              <span className="choice-hint">{role.description}</span>
            </label>
          ))}
        </FieldSet>

        <div className="form-actions">
          <button type="submit" className="btn" disabled={form.busy}>
            {partner ? '저장' : '등록'}
          </button>
          {partner && (
            <ActivationButton
              record={partner}
              busy={form.busy}
              onDeactivate={() => void form.run(() => partnerApi.deactivate(partner.id), '사용 중지했습니다.')}
              onActivate={() => void form.run(() => partnerApi.activate(partner.id), '다시 사용합니다.')}
            />
          )}
        </div>
      </form>
    </FormPanel>
  )
}
