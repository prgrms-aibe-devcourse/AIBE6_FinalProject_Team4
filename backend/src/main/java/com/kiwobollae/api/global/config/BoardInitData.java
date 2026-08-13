package com.kiwobollae.api.global.config;

import com.kiwobollae.api.auth.entity.User;
import com.kiwobollae.api.auth.repository.UserRepository;
import com.kiwobollae.api.board.entity.BoardComment;
import com.kiwobollae.api.board.entity.BoardPost;
import com.kiwobollae.api.board.entity.BoardPostLike;
import com.kiwobollae.api.board.entity.enums.BoardCategory;
import com.kiwobollae.api.board.repository.BoardCommentRepository;
import com.kiwobollae.api.board.repository.BoardPostLikeRepository;
import com.kiwobollae.api.board.repository.BoardPostRepository;
import com.kiwobollae.api.journal.entity.PlantJournal;
import com.kiwobollae.api.journal.repository.PlantJournalRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-only sample board posts/comments/likes so /board has a full page of
 * content on a fresh DB (list default page size is 20). Covers all three
 * categories (NOTICE/FREE/PLANT_QNA) and a handful of reply-to-reply threads
 * so the nested-comment UI and pagination both have something real to show.
 *
 * <p>Depends on InitData (users, @Order(1)) and PlantJournalInitData (journals,
 * @Order(4)) having already run — PLANT_QNA posts link to whichever journals
 * exist for test@test.com, falling back to no journal link if none exist.
 *
 * <p>Disable without changing code by setting {@code app.seed.board.enabled=false}.
 */
@Component
@Profile({"local", "prod"})
@ConditionalOnProperty(prefix = "app.seed.board", name = "enabled", havingValue = "true")
@Order(6)
@RequiredArgsConstructor
public class BoardInitData implements ApplicationRunner {

	private record PostSeed(BoardCategory category, String author, String title, String content) {
	}

	// parentSeedIndex는 commentSeeds 리스트 안에서의 절대 인덱스를 가리킨다(같은 게시글 내 순서가 아님).
	private record CommentSeed(int postIndex, String author, String content, Integer parentSeedIndex) {
	}

	private final UserRepository userRepository;
	private final PlantJournalRepository plantJournalRepository;
	private final BoardPostRepository boardPostRepository;
	private final BoardCommentRepository boardCommentRepository;
	private final BoardPostLikeRepository boardPostLikeRepository;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (boardPostRepository.count() > 0) {
			return;
		}

		User admin = userRepository.findByEmail("admin@test.com").orElse(null);
		User test = userRepository.findByEmail("test@test.com").orElse(null);
		User user = userRepository.findByEmail("user@test.com").orElse(null);
		if (admin == null || test == null || user == null) {
			return;
		}

		List<Long> journalIds = plantJournalRepository
				.search(test.getId(), null, null, null, PageRequest.of(0, 5))
				.getContent().stream().map(PlantJournal::getId).toList();

		List<PostSeed> seeds = List.of(
				new PostSeed(BoardCategory.NOTICE, "admin", "커뮤니티 게시판 오픈 안내 🌱",
						"키워볼래 커뮤니티 게시판이 열렸어요! 식물 이야기, 궁금한 점 자유롭게 나눠주세요."),
				new PostSeed(BoardCategory.NOTICE, "admin", "게시판 이용 규칙 안내",
						"서로 존중하는 말투 부탁드려요. 광고성 게시글이나 도배는 예고 없이 숨김 처리될 수 있어요."),
				new PostSeed(BoardCategory.NOTICE, "admin", "8월 정기 점검 안내 (8/15 02:00~04:00)",
						"안정적인 서비스 운영을 위해 정기 점검을 진행해요. 점검 시간 동안은 접속이 잠시 제한될 수 있습니다."),
				new PostSeed(BoardCategory.FREE, "test", "다들 베란다 온도 어떻게 관리하세요?",
						"요즘 낮밤 기온 차가 심해서 베란다 화분들이 걱정이에요. 다들 어떻게 관리하시나요?"),
				new PostSeed(BoardCategory.FREE, "user", "오늘 첫 수확했어요! 🍅",
						"작은 방울토마토였는데 직접 키운 거라 그런지 엄청 달아요. 다들 첫 수확 기억 있으세요?"),
				new PostSeed(BoardCategory.FREE, "admin", "다육이 키우기 입문자 추천 종류 있을까요",
						"물주기를 자주 깜빡하는 편인데 그래도 잘 버텨주는 다육이 종류 추천 부탁드려요."),
				new PostSeed(BoardCategory.FREE, "test", "화분 흙 냄새가 나는데 정상인가요?",
						"물을 준 다음날부터 흙에서 살짝 쿰쿰한 냄새가 나요. 과습인 걸까요?"),
				new PostSeed(BoardCategory.FREE, "user", "베란다 텃밭 사진 공유해요 📸",
						"주말마다 조금씩 채소 모종을 늘리고 있어요. 상추랑 깻잎이 제일 잘 자라네요."),
				new PostSeed(BoardCategory.FREE, "test", "식물 영양제 vs 비료, 뭐가 더 나을까요",
						"둘 다 써보긴 했는데 차이를 잘 모르겠어요. 경험담 들려주세요."),
				new PostSeed(BoardCategory.FREE, "user", "장마철 화분 관리 팁 공유합니다",
						"비 많이 오는 날은 화분을 처마 밑으로 옮기는 게 제일 확실한 것 같아요."),
				new PostSeed(BoardCategory.FREE, "admin", "식물 이름 짓는 나만의 규칙 있으신가요?",
						"저는 종류 앞글자 따서 짓는데 다들 어떻게 이름 지으시는지 궁금해요 ㅎㅎ"),
				new PostSeed(BoardCategory.FREE, "test", "분갈이 시기 놓친 것 같은데 지금 해도 될까요",
						"봄에 했어야 했는데 미루다가 지금까지 왔어요. 여름 분갈이 괜찮을까요?"),
				new PostSeed(BoardCategory.FREE, "user", "반려식물 이름 뭘로 지으셨어요?",
						"저는 첫 식물한테 '토실이'라고 지어줬어요. 다들 이름 자랑해주세요!"),
				new PostSeed(BoardCategory.FREE, "admin", "식물 키우면서 제일 뿌듯했던 순간",
						"죽어가던 화분을 살려냈을 때가 제일 뿌듯했어요. 여러분은 언제 그러셨나요?"),
				new PostSeed(BoardCategory.FREE, "test", "실내 습도 어느 정도로 맞추고 계세요?",
						"가습기를 틀어야 하나 고민 중이에요. 적정 습도 기준이 있을까요?"),
				new PostSeed(BoardCategory.PLANT_QNA, "test", "잎끝이 갈색으로 마르는데 이유가 뭘까요?",
						"최근 일지에 남긴 것처럼 잎끝부터 갈색으로 마르기 시작했어요. 물은 평소대로 주고 있는데 원인을 모르겠어요."),
				new PostSeed(BoardCategory.PLANT_QNA, "test", "새잎이 나오다가 멈췄어요",
						"2주 전까지는 새잎이 계속 나왔는데 요즘은 그대로예요. 계절 때문일까요?"),
				new PostSeed(BoardCategory.PLANT_QNA, "test", "화분 밑으로 벌레가 보이는데 어떻게 하나요",
						"흙 표면 근처에서 작은 날벌레가 보여요. 방제 방법 아시는 분 계실까요?"),
				new PostSeed(BoardCategory.PLANT_QNA, "test", "물주기 주기를 얼마나 둬야 할까요",
						"지금은 3일에 한 번 주고 있는데 흙이 계속 축축한 것 같아서요."),
				new PostSeed(BoardCategory.PLANT_QNA, "test", "줄기가 갑자기 휘었어요, 지지대 필요할까요",
						"한쪽으로 계속 자라서 그런지 줄기가 휘기 시작했어요. 지지대를 세워줘야 할지 궁금해요.")
		);

		int qnaStart = (int) seeds.stream().filter(s -> s.category() != BoardCategory.PLANT_QNA).count();
		List<BoardPost> posts = new ArrayList<>();
		for (int i = 0; i < seeds.size(); i++) {
			PostSeed seed = seeds.get(i);
			User author = resolveUser(seed.author(), admin, test, user);
			Long journalId = null;
			if (seed.category() == BoardCategory.PLANT_QNA) {
				int journalIndex = i - qnaStart;
				journalId = journalIndex < journalIds.size() ? journalIds.get(journalIndex) : null;
			}
			posts.add(boardPostRepository.save(
					BoardPost.create(author, seed.category(), seed.title(), seed.content(), journalId)));
		}

		List<CommentSeed> commentSeeds = List.of(
				new CommentSeed(3, "user", "저는 밤에는 뽁뽁이로 감싸줘요! 확실히 효과 있더라고요.", null),
				new CommentSeed(3, "test", "오 뽁뽁이 좋은 방법이네요, 저도 해봐야겠어요.", 0),
				new CommentSeed(3, "user", "네! 대신 낮에는 꼭 벗겨주세요, 안 그러면 습기 차더라고요.", 1),
				new CommentSeed(4, "admin", "우와 색이 정말 예쁘네요! 축하드려요 🎉", null),
				new CommentSeed(4, "test", "저도 이번 주말에 첫 수확 도전해봐야겠어요.", null),
				new CommentSeed(5, "test", "스투키나 산세베리아 추천드려요, 거의 안 죽어요.", null),
				new CommentSeed(5, "user", "저도 스투키로 시작했는데 정말 튼튼하더라고요!", 5),
				new CommentSeed(6, "user", "과습 냄새일 수 있어요, 물 주는 간격을 좀 늘려보세요.", null),
				new CommentSeed(7, "admin", "사진 잘 봤어요! 상추 정말 싱싱해 보이네요.", null),
				new CommentSeed(9, "admin", "저는 장마철엔 아예 실내로 들여요, 그게 제일 편하더라고요.", null),
				new CommentSeed(11, "test", "지금이라도 늦지 않았어요! 뿌리 상태만 확인하고 진행해보세요.", null),
				new CommentSeed(15, "user", "습도가 낮아서 그럴 수 있어요! 가습기나 분무 한번 해보세요.", null),
				new CommentSeed(15, "test", "감사해요, 오늘부터 분무 해볼게요!", 11),
				new CommentSeed(16, "user", "온도 변화가 큰 계절이라 잠시 성장을 멈춘 걸 수도 있어요.", null),
				new CommentSeed(17, "admin", "혹시 코바늘 벌레라면 흙 표면에 계핏가루를 뿌려보세요.", null),
				new CommentSeed(17, "test", "오 계핏가루는 처음 들어봐요, 시도해볼게요!", 14),
				new CommentSeed(18, "user", "겉흙이 마른 뒤에 주는 걸로 바꿔보시는 게 어떨까요?", null),
				new CommentSeed(19, "admin", "가벼운 지지대 하나 세워주면 훨씬 안정적일 거예요.", null)
		);

		BoardComment[] savedComments = new BoardComment[commentSeeds.size()];
		for (int i = 0; i < commentSeeds.size(); i++) {
			CommentSeed seed = commentSeeds.get(i);
			BoardPost post = posts.get(seed.postIndex());
			User author = resolveUser(seed.author(), admin, test, user);
			BoardComment parent = seed.parentSeedIndex() != null ? savedComments[seed.parentSeedIndex()] : null;
			BoardComment saved = boardCommentRepository.save(
					BoardComment.create(post, author, seed.content(), parent));
			savedComments[i] = saved;
			post.incrementCommentCount();
		}

		List<User> likers = List.of(admin, test, user);
		for (int i = 0; i < posts.size(); i++) {
			BoardPost post = posts.get(i);
			int likeCount = 1 + (i % 3);
			for (int l = 0; l < likeCount; l++) {
				User liker = likers.get(l % likers.size());
				if (liker.getId().equals(post.getUser().getId())) {
					continue;
				}
				boardPostLikeRepository.save(BoardPostLike.create(post, liker, LocalDateTime.now()));
				post.incrementLikeCount();
			}
		}
	}

	private User resolveUser(String key, User admin, User test, User user) {
		return switch (key) {
			case "admin" -> admin;
			case "test" -> test;
			default -> user;
		};
	}
}
