import { useNavigate, Link } from 'react-router-dom';

type Role = 'provider' | 'annotator';

const roles: { key: Role; title: string; desc: string }[] = [
  {
    key: 'provider',
    title: '数据集提供者',
    desc: '发布数据标注需求、管理数据集和查看标注进度',
  },
  {
    key: 'annotator',
    title: '数据标注员',
    desc: '领取标注任务、执行数据标注并提交结果',
  },
];

export function RoleSelect() {
  const navigate = useNavigate();

  function handleSelect(role: Role) {
    navigate(`/login?role=${role}`);
  }

  return (
    <div className="app-page app-center">
      <div className="app-stack app-stack-md">
        <div className="app-heading">
          <h1 className="app-title">数据标注系统</h1>
          <p className="app-subtitle">请选择您的身份以继续登录</p>
        </div>

        <div className="app-choice-grid">
          {roles.map((role) => (
            <button
              key={role.key}
              onClick={() => handleSelect(role.key)}
              className="app-choice-card group cursor-pointer"
            >
              <div className="app-choice-title">{role.title}</div>
              <p className="app-choice-desc">{role.desc}</p>
              <div className="app-choice-arrow">
                <svg
                  className="app-choice-arrow-icon"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </div>
            </button>
          ))}
        </div>

        <div className="mt-8 text-center">
          <Link to="/api-test" className="text-sm text-gray-400 hover:text-gray-600">
            API 测试页面 →
          </Link>
        </div>
      </div>
    </div>
  );
}
