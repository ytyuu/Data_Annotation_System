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
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="w-full max-w-2xl px-6">
        <div className="text-center mb-10">
          <h1 className="text-3xl font-bold text-gray-900">数据标注系统</h1>
          <p className="mt-2 text-gray-500">请选择您的身份以继续登录</p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
          {roles.map((role) => (
            <button
              key={role.key}
              onClick={() => handleSelect(role.key)}
              className="group relative bg-white border-2 border-gray-200 rounded-xl p-8 text-left hover:border-blue-500 hover:shadow-lg transition-all duration-200 cursor-pointer"
            >
              <div className="text-xl font-semibold text-gray-900 group-hover:text-blue-600">
                {role.title}
              </div>
              <p className="mt-3 text-sm text-gray-500 leading-relaxed">
                {role.desc}
              </p>
              <div className="absolute top-4 right-4 w-8 h-8 rounded-full bg-gray-100 group-hover:bg-blue-500 flex items-center justify-center transition-colors">
                <svg
                  className="w-4 h-4 text-gray-400 group-hover:text-white transition-colors"
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
