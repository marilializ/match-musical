/**
 * Modo demonstração: um "servidor" em memória com a mesma interface de api.js.
 * Entra quando o banco não responde, para a interface poder ser explorada sem MySQL.
 * Nada aqui é gravado; recarregar a página recomeça.
 */
import { nowBR, todayBR, norm } from './format.js';
import { COMPATIBLE, BELOW_LEVEL } from './domain.js';

const MUSICIANS = [
  { id: 1, name: 'Marina Costa', artist: 'Marina Costa', level: 'Intermediario', category: 'Voz', bio: 'Cantora apaixonada por novas sonoridades e boas conexões.' },
  { id: 2, name: 'Lucas Andrade', artist: 'Lucas Andrade', level: 'Avancado', category: 'Guitarra', bio: 'Guitarrista em busca de projetos para criar e tocar ao vivo.' },
  { id: 3, name: 'Ana Beatriz', artist: 'Ana B.', level: 'Basico', category: 'Teclado', bio: 'Aprendendo, criando e encontrando meu lugar na música.' },
];

const GROUPS = [
  { id: 31, name: 'Banda Horizonte', city: 'Recife', type: 'Banda', genres: 'Indie, Rock alternativo', description: 'Rock alternativo com letras em português. Ensaios aos sábados.', openVacancies: 2 },
  { id: 37, name: 'Maré Jazz', city: 'Recife', type: 'Banda', genres: 'Jazz, Bossa nova', description: 'Noites de jazz no Recife Antigo. Procuramos quem goste de improvisar.', openVacancies: 2 },
  { id: 40, name: 'Frevo Elétrico', city: 'Olinda', type: 'Banda', genres: 'Frevo, Rock', description: 'Metais, guitarra e muito carnaval o ano inteiro.', openVacancies: 2 },
  { id: 32, name: 'Orquestra Recife', city: 'Recife', type: 'Orquestra', genres: 'Música clássica, Trilhas', description: 'Repertório erudito e trilhas de cinema. Temporada nova em março.', openVacancies: 2 },
  { id: 33, name: 'Coral Vozes', city: 'Olinda', type: 'Coral', genres: 'Gospel, MPB', description: 'Vozes diferentes, uma só energia.', openVacancies: 1 },
  { id: 34, name: 'Fogo Cruzado', city: 'Caruaru', type: 'Banda', genres: 'Reggae', description: 'Ritmo, presença e vontade de tocar juntos.', openVacancies: 1 },
  { id: 36, name: 'Trio Nordestino', city: 'Paulista', type: 'Banda', genres: 'Forró, Baião', description: 'Sanfona, zabumba e triângulo. Falta você.', openVacancies: 1 },
  { id: 39, name: 'Camerata Capibaribe', city: 'Recife', type: 'Orquestra', genres: 'Barroco, Clássico', description: 'Pequena orquestra de cordas com concertos mensais.', openVacancies: 1 },
  { id: 35, name: 'Rock das Antigas', city: 'Petrolina', type: 'Banda', genres: 'Rock clássico', description: 'Groove e história em cada apresentação.', openVacancies: 1 },
  { id: 38, name: 'Coral Encanto', city: 'Jaboatão', type: 'Coral', genres: 'Erudito', description: 'Canto coral para todas as idades.', openVacancies: 0 },
  { id: 41, name: 'Sons do Mangue', city: 'Recife', type: 'Banda', genres: 'Manguebeat', description: 'Tambores e guitarras.', openVacancies: 0, swiped: true },
  { id: 42, name: 'Orquestra Popular', city: 'Recife', type: 'Orquestra', genres: 'MPB, Choro', description: 'Música popular com arranjos para orquestra.', openVacancies: 0, swiped: true },
];

// shared = gêneros em comum com o perfil (o que a view de recomendação devolveria)
const VACANCIES = [
  { id: 1, title: 'Vocalista', groupId: 31, level: 'Intermediario', compatibility: COMPATIBLE, shared: 'Indie', description: 'Voz para rock alternativo, com segunda voz nos refrões.' },
  { id: 2, title: 'Baterista', groupId: 31, level: 'Intermediario', compatibility: COMPATIBLE, shared: 'Rock alternativo', description: 'Bateria para shows de sexta e sábado no Recife.' },
  { id: 9, title: 'Saxofonista', groupId: 37, level: 'Avancado', compatibility: BELOW_LEVEL, description: 'Improviso e leitura de partitura para noites de jazz.' },
  { id: 10, title: 'Pianista', groupId: 37, level: 'Intermediario', compatibility: COMPATIBLE, shared: 'Bossa nova', description: 'Piano acústico e teclado para bossa nova.' },
  { id: 12, title: 'Trompetista', groupId: 40, level: 'Basico', compatibility: COMPATIBLE, description: 'Naipe de metais para o carnaval. Ensaios em Olinda.' },
  { id: 13, title: 'Guitarrista', groupId: 40, level: 'Intermediario', compatibility: COMPATIBLE, shared: 'Rock', description: 'Guitarra com distorção e frevo na mesma música.' },
  { id: 3, title: 'Violinista', groupId: 32, level: 'Avancado', compatibility: BELOW_LEVEL, description: 'Primeiro violino para a temporada de março.' },
  { id: 4, title: 'Violoncelista', groupId: 32, level: 'Profissional', compatibility: BELOW_LEVEL, description: 'Violoncelo para repertório erudito e trilhas.' },
  { id: 5, title: 'Soprano', groupId: 33, level: 'Basico', compatibility: COMPATIBLE, shared: 'MPB', description: 'Soprano para o coral. Não precisa ler partitura.' },
  { id: 6, title: 'Baixista', groupId: 34, level: 'Intermediario', compatibility: COMPATIBLE, description: 'Baixo para reggae, com groove e paciência.' },
  { id: 8, title: 'Sanfoneiro', groupId: 36, level: 'Intermediario', compatibility: COMPATIBLE, description: 'Sanfona para forró pé de serra.' },
  { id: 11, title: 'Violista', groupId: 39, level: 'Intermediario', compatibility: COMPATIBLE, description: 'Viola para quarteto de cordas.' },
  { id: 7, title: 'Guitarrista', groupId: 35, level: 'Avancado', compatibility: BELOW_LEVEL, description: 'Guitarra solo para covers dos anos 70.' },
];

const MATCHES = [
  { id: 1, otherId: 41, name: 'Sons do Mangue', otherIsGroup: true, status: 'Ativo', created: '15/09/2026' },
  { id: 2, otherId: 42, name: 'Orquestra Popular', otherIsGroup: true, status: 'Convertido', created: '19/09/2026' },
  { id: 3, otherId: 2, name: 'Lucas Andrade', otherIsGroup: false, status: 'Ativo', created: '02/10/2026' },
];

const LOG = [
  { match: 2, from: 'Ativo', to: 'Convertido', when: '20/09/2026 18:40' },
  { match: 1, from: 'Pendente', to: 'Ativo', when: '15/09/2026 10:12' },
];

const MEMBERS = {
  31: [{ id: 11, name: 'Rafael Lima', role: 'Guitarrista', since: '03/03/2024' }, { id: 12, name: 'Bia Torres', role: 'Baixista', since: '12/08/2024' }],
  32: [{ id: 13, name: 'Helena Prado', role: 'Spalla', since: '01/02/2022' }, { id: 14, name: 'Caio Mendes', role: 'Violoncelo', since: '10/06/2023' }, { id: 15, name: 'Lia Duarte', role: 'Flauta', since: '15/01/2025' }],
  33: [{ id: 16, name: 'Joana Reis', role: 'Contralto', since: '05/05/2023' }],
  37: [{ id: 17, name: 'Téo Barros', role: 'Contrabaixo', since: '20/09/2024' }, { id: 18, name: 'Duda Melo', role: 'Bateria', since: '02/02/2025' }],
  40: [{ id: 19, name: 'Nina Rocha', role: 'Trombone', since: '18/02/2023' }],
  42: [{ id: 1, name: 'Marina Costa', role: 'Voz', since: '20/09/2026' }],
};

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const fail = (message, status = 400) => Object.assign(new Error(message), { status });

export function createDemoBackend() {
  const db = {
    groups: structuredClone(GROUPS).map((group) => ({ status: 'Ativo', swiped: false, ...group })),
    vacancies: structuredClone(VACANCIES),
    matches: structuredClone(MATCHES),
    log: structuredClone(LOG),
  };
  let nextMatchId = 100;
  const groupOf = (id) => db.groups.find((group) => group.id === Number(id));
  const vacancyView = (vacancy) => {
    const group = groupOf(vacancy.groupId);
    return { ...vacancy, group: group.name, city: group.city };
  };

  return {
    async bootstrap() {
      return structuredClone({
        musicians: MUSICIANS,
        groups: db.groups,
        vacancies: db.vacancies.map(vacancyView),
        matches: db.matches,
        stats: { groups: db.groups.length, vacancies: db.vacancies.length, matches: db.matches.length, rate: 66.67 },
        matchLog: db.log,
        objects: { fn_compatibilidade_vaga: true, fn_taxa_retribuicao_usuario: true, vw_recomendacao_vagas: true, vw_integrantes_grupo: true },
      });
    },

    async swipe(user, target, decision) {
      const group = groupOf(target);
      if (!group || group.status !== 'Ativo') throw fail('Grupo inativo.', 404);
      if (group.swiped) throw fail('Você já avaliou este grupo.', 409);
      group.swiped = true;
      // Regra da demonstração: super like sempre dá match; like dá match em grupo com 2+ vagas.
      const matched = decision === 'Super Like' || (decision === 'Like' && group.openVacancies >= 2);
      if (!matched) return { ok: true, matched: false, matchId: null };
      const match = { id: nextMatchId++, otherId: group.id, name: group.name, otherIsGroup: true, status: 'Ativo', created: todayBR() };
      db.matches.unshift(match);
      return { ok: true, matched: true, matchId: match.id };
    },

    async recommended(user, { funcao = '', nivel = '' } = {}) {
      await wait(160);
      const recommended = db.vacancies.filter((v) => v.compatibility === COMPATIBLE && v.shared);
      const query = norm(funcao);
      const rows = recommended
        .filter((v) => (!query || norm(v.title).includes(query)) && (!nivel || v.level === nivel))
        .map((v) => ({ ...vacancyView(v), genres: v.shared, sharedGenres: v.shared.split(',').length }));
      return {
        rows,
        indicator: {
          open: db.vacancies.length,
          compatible: db.vacancies.filter((v) => v.compatibility === COMPATIBLE).length,
          recommended: recommended.length,
        },
      };
    },

    async members(group) {
      await wait(160);
      return { rows: structuredClone(MEMBERS[group] ?? []) };
    },

    async matchLog(user, { match = '', situacao = '' } = {}) {
      await wait(120);
      const rows = db.log.filter((entry) => (!match || entry.match === Number(match)) && (!situacao || entry.to === situacao));
      return { rows: structuredClone(rows) };
    },

    async convert(user, matchId, role) {
      await wait(400);
      const match = db.matches.find((m) => m.id === Number(matchId));
      if (!match || match.status !== 'Ativo') throw fail('Só um match Ativo pode ser convertido.');
      if (!String(role).trim()) throw fail('Informe a sua função no grupo.');
      match.status = 'Convertido';
      db.log.unshift({ match: match.id, from: 'Ativo', to: 'Convertido', when: nowBR() });
      return { ok: true };
    },

    async syncGroups() {
      throw fail('Disponível só com o banco conectado.', 501);
    },
  };
}
