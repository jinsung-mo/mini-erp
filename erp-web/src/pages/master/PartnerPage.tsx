import { useState } from 'react'
import { PARTNER_ROLES, type PartnerRole, type PartnerSearch, partnerApi } from '../../api/partners'
import { formatBusinessNo } from '../../format'
import { ActiveFilterSelect, MasterGrid } from './MasterParts'
import { PartnerForm } from './PartnerForm'
import { type ActiveFilter, toActive, useMasterPage } from './useMasterPage'

type Filter = { keyword: string; role: PartnerRole | 'all'; active: ActiveFilter }

const INITIAL_FILTER: Filter = { keyword: '', role: 'all', active: 'active' }

function toSearch(filter: Filter): PartnerSearch {
  return {
    keyword: filter.keyword.trim() || undefined,
    role: filter.role === 'all' ? undefined : filter.role,
    active: toActive(filter.active),
  }
}

/** 공급처·고객 표시. 둘 다인 거래처는 태그가 두 개 붙는다 */
function RoleTags({ roles }: { roles: PartnerRole[] }) {
  return (
    <span className="tags">
      {PARTNER_ROLES.filter((role) => roles.includes(role.value)).map((role) => (
        <span key={role.value} className={`tag tag-${role.value.toLowerCase()}`}>
          {role.label}
        </span>
      ))}
    </span>
  )
}

/**
 * 거래처 화면. 품목 화면과 같은 틀에 "구분(공급처·고객)" 조건과 사업자등록번호가 더해진다.
 * 2단계 발주에서는 공급처만, 4단계 수주에서는 고객만 이 목록에서 고른다.
 */
export function PartnerPage() {
  const [filter, setFilter] = useState<Filter>(INITIAL_FILTER)
  const page = useMasterPage(partnerApi.search, toSearch(INITIAL_FILTER))

  return (
    <section className="page page-wide">
      <div className="page-header">
        <div>
          <h1>거래처</h1>
          <p className="page-desc">
            물건을 사 오는 공급처와 파는 고객을 함께 관리합니다. 한 회사는 한 번만 등록하고, 공급처이면서 고객이면
            구분을 둘 다 선택합니다.
          </p>
        </div>
        <button type="button" className="btn" onClick={() => page.select({ mode: 'create' })}>
          신규 등록
        </button>
      </div>

      <form
        className="filter-bar"
        onSubmit={(event) => {
          event.preventDefault()
          page.search(toSearch(filter))
        }}
      >
        <label>
          검색어
          <input
            name="keyword"
            value={filter.keyword}
            onChange={(e) => setFilter({ ...filter, keyword: e.target.value })}
            placeholder="코드, 거래처명, 사업자등록번호"
          />
        </label>
        <label>
          구분
          <select
            name="role"
            value={filter.role}
            onChange={(e) => setFilter({ ...filter, role: e.target.value as Filter['role'] })}
          >
            <option value="all">전체</option>
            {PARTNER_ROLES.map((role) => (
              <option key={role.value} value={role.value}>
                {role.label}
              </option>
            ))}
          </select>
        </label>
        <ActiveFilterSelect value={filter.active} onChange={(active) => setFilter({ ...filter, active })} />
        <button type="submit" className="btn btn-secondary" disabled={page.loading}>
          조회
        </button>
      </form>

      <div className={page.selection ? 'split' : undefined}>
        <MasterGrid
          columns={['코드', '거래처명', '사업자등록번호', '구분']}
          rows={page.rows}
          loading={page.loading}
          error={page.error}
          selectedId={page.editing?.id ?? null}
          emptyText="조회된 거래처가 없습니다."
          onSelect={(partner) => page.select({ mode: 'edit', record: partner })}
          renderCells={(partner) => (
            <>
              <td>
                <code>{partner.code}</code>
              </td>
              <td>{partner.name}</td>
              <td>{partner.businessNo ? formatBusinessNo(partner.businessNo) : '-'}</td>
              <td>
                <RoleTags roles={partner.roles} />
              </td>
            </>
          )}
        />

        {page.selection && (
          <PartnerForm
            key={page.formKey}
            partner={page.editing}
            notice={page.notice}
            onSaved={page.saved}
            onClose={() => page.select(null)}
          />
        )}
      </div>
    </section>
  )
}
