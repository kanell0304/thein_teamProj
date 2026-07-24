-- 채팅 키워드 추출 기본 카테고리. 기존 행과 사용자가 수정한 별칭은 덮어쓰지 않는다.
INSERT INTO food_keywords (keyword_type, name, aliases)
VALUES
    ('CATEGORY', '한식', '["한국음식", "한국 음식", "한국요리", "한국 요리"]'::jsonb),
    ('CATEGORY', '중식', '["중국음식", "중국 음식", "중국요리", "중국 요리"]'::jsonb),
    ('CATEGORY', '일식', '["일본음식", "일본 음식", "일본요리", "일본 요리"]'::jsonb),
    ('CATEGORY', '양식', '["서양음식", "서양 음식", "서양요리", "서양 요리"]'::jsonb),
    ('CATEGORY', '아시안', '["아시아음식", "아시아 음식", "동남아음식", "동남아 음식"]'::jsonb),
    ('CATEGORY', '분식', '["분식집"]'::jsonb),
    ('CATEGORY', '카페', '["커피전문점", "커피 전문점", "커피숍"]'::jsonb),
    ('CATEGORY', '베이커리', '["제과점", "빵집"]'::jsonb),
    ('CATEGORY', '디저트', '["후식", "디저트카페", "디저트 카페"]'::jsonb),
    ('CATEGORY', '패스트푸드', '["패스트 푸드"]'::jsonb),
    ('CATEGORY', '고기요리', '["육류요리", "육류 요리", "고깃집", "고기집"]'::jsonb),
    ('CATEGORY', '해산물요리', '["해산물 요리", "해물요리", "해물 요리", "횟집"]'::jsonb)
ON CONFLICT (keyword_type, name) DO NOTHING;

-- 채팅 키워드 추출 기본 메뉴. 행 순서는 같은 점수일 때의 안정적인 등록 순서가 된다.
INSERT INTO food_keywords (keyword_type, name, aliases)
VALUES
    ('MENU', '초밥', '["스시"]'::jsonb),
    ('MENU', '돈가스', '["돈까스", "돈카츠"]'::jsonb),
    ('MENU', '치킨', '["프라이드치킨", "후라이드치킨", "프라이드", "후라이드", "양념치킨"]'::jsonb),
    ('MENU', '피자', '[]'::jsonb),
    ('MENU', '햄버거', '["버거"]'::jsonb),
    ('MENU', '떡볶이', '[]'::jsonb),
    ('MENU', '김밥', '[]'::jsonb),
    ('MENU', '라면', '[]'::jsonb),
    ('MENU', '국밥', '["돼지국밥", "순대국밥"]'::jsonb),
    ('MENU', '삼겹살', '["삼겹"]'::jsonb),
    ('MENU', '닭갈비', '[]'::jsonb),
    ('MENU', '불고기', '["불백"]'::jsonb),
    ('MENU', '제육볶음', '["제육"]'::jsonb),
    ('MENU', '김치찌개', '["김치찌게"]'::jsonb),
    ('MENU', '된장찌개', '["된장찌게"]'::jsonb),
    ('MENU', '비빔밥', '[]'::jsonb),
    ('MENU', '냉면', '["물냉면", "비빔냉면"]'::jsonb),
    ('MENU', '칼국수', '["바지락칼국수", "닭칼국수"]'::jsonb),
    ('MENU', '만두', '[]'::jsonb),
    ('MENU', '짜장면', '["자장면"]'::jsonb),
    ('MENU', '짬뽕', '[]'::jsonb),
    ('MENU', '탕수육', '[]'::jsonb),
    ('MENU', '마라탕', '[]'::jsonb),
    ('MENU', '양꼬치', '["양고기꼬치", "양고기 꼬치"]'::jsonb),
    ('MENU', '파스타', '["스파게티"]'::jsonb),
    ('MENU', '샐러드', '[]'::jsonb),
    ('MENU', '쌀국수', '["베트남쌀국수", "베트남 쌀국수"]'::jsonb),
    ('MENU', '카레', '["커리"]'::jsonb),
    ('MENU', '샤브샤브', '["샤브 샤브"]'::jsonb),
    ('MENU', '우동', '[]'::jsonb),
    ('MENU', '라멘', '["일본라면", "일본 라면"]'::jsonb),
    ('MENU', '족발', '[]'::jsonb),
    ('MENU', '보쌈', '[]'::jsonb),
    ('MENU', '장어구이', '["장어"]'::jsonb),
    ('MENU', '생선회', '["사시미"]'::jsonb)
ON CONFLICT (keyword_type, name) DO NOTHING;

-- 실제 존재하는 음식점 데이터 한개를 삽입한다.
INSERT INTO restaurants (
    kakao_place_id,
    name,
    category,
    road_address,
    address,
    latitude,
    longitude
)
VALUES (
    '26814353',
    '딸부자네불백 강남역점',
    '한식',
    '서울 강남구 봉은사로6길 38',
    '서울 강남구 역삼동 619',
    37.5025706,
    127.0275938
)
ON CONFLICT (kakao_place_id) DO NOTHING;
