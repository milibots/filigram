<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit();
}

if (isset($_GET['action']) && $_GET['action'] === 'proxy') {
    $url = $_GET['url'] ?? '';
    if (filter_var($url, FILTER_VALIDATE_URL)) {
        $ext = pathinfo(parse_url($url, PHP_URL_PATH), PATHINFO_EXTENSION);
        if ($ext === 'vtt') header('Content-Type: text/vtt');
        elseif ($ext === 'srt') header('Content-Type: text/plain'); 
        header('Access-Control-Allow-Origin: *');
        
        $content = @file_get_contents($url);
        if ($content !== false) { echo $content; exit; }
    }
    http_response_code(404);
    exit('File not found or invalid URL');
}

if (isset($_GET['action']) && $_GET['action'] == 'proxy_sub') {
    $url = $_GET['url'];
    if (filter_var($url, FILTER_VALIDATE_URL)) {
        $ext = pathinfo(parse_url($url, PHP_URL_PATH), PATHINFO_EXTENSION);
        if ($ext === 'vtt') header('Content-Type: text/vtt; charset=utf-8');
        elseif ($ext === 'srt') header('Content-Type: text/plain; charset=utf-8');
        header('Access-Control-Allow-Origin: *'); 
        echo @file_get_contents($url);
    }
    exit;
}

if (isset($_GET['docs']) || (isset($_GET['action']) && $_GET['action'] == 'docs')) {
    $docs = [
        'api_name' => 'Movielix API Proxy (Ultimate Edition)',
        'version' => '4.0',
        'endpoints' => [
            'main' => ['method' => 'GET/POST', 'description' => 'Get main page with slider and categories'],
            'vitrin' => ['method' => 'GET/POST', 'description' => 'Get vitrin/showcase page with pagination'],
            'category_items' => ['method' => 'GET/POST', 'description' => 'Get full paginated items for a specific category ID'],
            'actor_details' => ['method' => 'GET/POST', 'description' => 'Get actor details and their paginated movies/series'],
            'search' => ['method' => 'GET/POST', 'description' => 'Search for movies and series'],
            'details' => ['method' => 'GET/POST', 'description' => 'Get detailed information'],
            'episodes' => ['method' => 'GET/POST', 'description' => 'Get series episodes'],
            'qualities' => ['method' => 'GET/POST', 'description' => 'Get available qualities'],
            'stream' => ['method' => 'GET/POST', 'description' => 'Get streaming URL'],
            'download' => ['method' => 'GET', 'description' => 'Direct download'],
            'genres' => ['method' => 'GET', 'description' => 'Get available genres for movies or series'],
            'by_genre' => ['method' => 'GET', 'description' => 'Get content by genre ID']
        ]
    ];
    echo json_encode($docs, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
    exit();
}

class MovielixAPI {
    private $base_url = "https://expertappmedia.org/api-v1";
    private $token = null;
    private $max_retries = 2;
    private $retry_delay = 1;
    private $token_file = 'movielix_token_cache.json';
    
    private $movie_genres = [
        400 => 'اکشن', 401 => 'ماجراجویی', 402 => 'کمدی', 403 => 'هیجانی',
        404 => 'علمی - تخیلی', 405 => 'انیمیشن', 406 => 'فانتزی', 407 => 'عاشقانه',
        408 => 'ترسناک', 409 => 'درام', 410 => 'جنایی', 411 => 'جنگی',
        412 => 'زندگی نامه', 413 => 'خانوادگی', 414 => 'تاریخی', 415 => 'موسیقی',
        416 => 'معمایی', 417 => 'ورزشی', 418 => 'وسترن', 419 => 'انیمه', 420 => 'مستند'
    ];
    
    private $series_genres = [
        421 => 'هالیودی', 422 => 'انیمه و انیمیشن', 423 => 'شرق آسیا', 424 => 'خاورمیانه و هند'
    ];
    
    public function __construct() {
        $this->loadOrRefreshToken();
    }
    
    private function loadOrRefreshToken() {
        if (file_exists($this->token_file)) {
            $cache = json_decode(file_get_contents($this->token_file), true);
            if ($cache && isset($cache['token']) && isset($cache['expires_at'])) {
                if (time() < $cache['expires_at']) {
                    $this->token = $cache['token'];
                    return true;
                }
            }
        }
        return $this->refreshToken();
    }
    
    private function refreshToken() {
        // دریافت آدرس Base URL بروزرسانی شده
        $baseUrlResponse = $this->getBaseUrl();
        if (isset($baseUrlResponse['url'])) {
            $this->base_url = rtrim($baseUrlResponse['url'], '/') . '/api-v1';
        }
        
        // درخواست ورژن جدید و تولید توکن معتبر
        $deviceData = [
            'apk_code' => '84',
            'apk_name' => '1.8.4',
            'device_version' => '9',
            'device_model' => 'G576D',
            'device_brand' => 'Google Phone',
            'device_api' => '28',
            'package' => 'com.expertapp.movielixmedia',
            'uniq' => '8f66ec3b-a880-4267-b287-74c6f841a2cf',
            'type' => '0',
            'token_firebase' => 'cDgipEqUSCq6-zVcj4LEwH:APA91bFsMIW63cV4Hs6AytZ42gsmgRg2YIVp8EA4wpvSqVrWwvrTQUoP6LkEtjNNeBmrdH08601xlIDGJT_RElRVkG8AR78lMPcFQNpozu8DR3aUk-P4q9g',
            'language' => '0',
            'market_type' => '0',
            'user_id' => '0',
            'mcc' => '208',
            'time_zone' => 'europe/paris'
        ];
        
        $response = $this->makeDirectRequest('/device/version', $deviceData, false);
        
        if ($response && isset($response['token']) && $response['status'] == 200) {
            $this->token = $response['token'];
            
            // شبیه‌سازی درخواست مهمان و تنظیم زبان (برای جلوگیری از بن شدن)
            $this->makeDirectRequest('/account/guest', [
                'token' => $this->token,
                'timezone' => 'europe/paris',
                'mcc' => '208'
            ], false);
            
            $this->makeDirectRequest('/language/set', [
                'token' => $this->token,
                'language' => '1'
            ], false);
            
            // ذخیره توکن
            $cache = [
                'token' => $this->token,
                'expires_at' => time() + (12 * 3600),
                'user_id' => $response['user_id'] ?? null,
                'device_id' => $response['device_id'] ?? null
            ];
            file_put_contents($this->token_file, json_encode($cache));
            
            return true;
        }
        
        // بک‌آپ در صورت بروز مشکل اینترنت
        $this->token = "178865496596627099";
        return false;
    }
    
    private function getBaseUrl() {
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, "https://global-api2.expertmedias.org/apiMovielix.php");
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, http_build_query(['movielix' => 'movielix']));
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_TIMEOUT, 10);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
        $response = curl_exec($ch);
        curl_close($ch);
        
        return json_decode($response, true) ?: [];
    }
    
    private function getHeaders() {
        $host = parse_url($this->base_url, PHP_URL_HOST) ?: "expertappmedia.org";
        return [
            "Accept-Encoding: gzip",
            "Connection: Keep-Alive",
            "Content-Type: application/x-www-form-urlencoded",
            "Host: " . $host,
            "User-Agent: okhttp/5.5.0",
            "X-App-Signature: AB20bZUC4SQcU/qhMDVfHWX+1liYELEI512YdfJAe+U=",
            "X-Device-Type: 0",
            "X-Device-Uniq: 8f66ec3b-a880-4267-b287-74c6f841a2cf",
            "X-Key-Password: 8edace647f7270c595cdadb594b450d6a2158a91f12aec20ce9b24d5fd59fa39b06537cee4831daf2f8d58d84b",
            "X-Key-Token: 23606005bfde651bd6118fc638121f99148bd81849d2abff082bf13c998e8ffb794cdf608bb0f48aeec5cd312f2535c8891a7ad727b202d7278ad590ade86eeb",
            "X-Key-Username: 0742ae2df5029929b81c9ce3b09151",
            "X-Package-Name: com.expertapp.movielixmedia",
            "X-Version-Code: 84",
            "X-Version-Name: 1.8.4"
        ];
    }
    
    private function makeDirectRequest($endpoint, $data, $useToken = true) {
        $url = $this->base_url . $endpoint;
        if ($useToken && $this->token) {
            $data['token'] = $this->token;
        }
        $postData = http_build_query($data);
        
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $postData);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_HTTPHEADER, $this->getHeaders());
        curl_setopt($ch, CURLOPT_ENCODING, "gzip");
        curl_setopt($ch, CURLOPT_TIMEOUT, 30);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
        
        $response = curl_exec($ch);
        curl_close($ch);
        
        return json_decode($response, true);
    }
    
    private function makeRequest($endpoint, $data, $retryCount = 0) {
        if (!$this->token) {
            $this->loadOrRefreshToken();
        }
        
        $url = $this->base_url . $endpoint;
        $data['token'] = $this->token;
        $postData = http_build_query($data);
        
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $postData);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_HTTPHEADER, $this->getHeaders());
        curl_setopt($ch, CURLOPT_ENCODING, "gzip");
        curl_setopt($ch, CURLOPT_TIMEOUT, 30);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
        
        $response = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $error = curl_error($ch);
        curl_close($ch);
        
        $result = json_decode($response, true);
        
        // اگر دستگاه بن شده بود یا توکن اشتباه بود، کش را خالی و توکن جدید میگیریم
        if ($result && isset($result['message']) && 
            (strpos($result['message'], 'اجازه استفاده') !== false || 
             strpos($result['message'], 'توکن ارسالی اشتباه') !== false || 
             strpos($result['message'], 'token') !== false)) {
            
            if ($retryCount < $this->max_retries) {
                if (file_exists($this->token_file)) unlink($this->token_file);
                $this->refreshToken();
                sleep($this->retry_delay);
                return $this->makeRequest($endpoint, $data, $retryCount + 1);
            }
        }
        
        if ($error || ($httpCode !== 200 && $retryCount < $this->max_retries)) {
            sleep($this->retry_delay);
            return $this->makeRequest($endpoint, $data, $retryCount + 1);
        }
        
        return $result ?: ['error' => 'Invalid JSON response', 'raw' => $response];
    }
    
    public function getMovieGenres() {
        $genres = [];
        foreach ($this->movie_genres as $id => $name) {
            $genres[] = ['id' => $id, 'name' => $name, 'type' => 'movie'];
        }
        return ['success' => true, 'category' => 'فیلم', 'total' => count($genres), 'genres' => $genres];
    }
    
    public function getSeriesGenres() {
        $genres = [];
        foreach ($this->series_genres as $id => $name) {
            $genres[] = ['id' => $id, 'name' => $name, 'type' => 'series'];
        }
        return ['success' => true, 'category' => 'سریال', 'total' => count($genres), 'genres' => $genres];
    }
    
    public function getAllGenres() {
        return ['success' => true, 'movies' => $this->getMovieGenres(), 'series' => $this->getSeriesGenres()];
    }
    
    public function getMainPage($type = 0, $action = 0, $genre_id = -1, $page = 1) {
        return $this->makeRequest('/home/main-page', ['type' => $type, 'action' => $action, 'genre_id' => $genre_id, 'page' => $page]);
    }
    
    public function getCategoryContent($id, $page = 1) {
        return $this->makeRequest('/home/movie', ['id' => $id, 'page' => $page]);
    }
    
    public function getActorDetails($id, $page = 1) {
        $response = $this->makeRequest('/actor/detail', ['id' => $id, 'page' => $page]);
        if (isset($response['error'])) return $response;
        
        $formattedData = [
            'success' => true,
            'actor' => $response['info'] ?? null,
            'page' => (int)$page,
            'has_more' => false,
            'total_items' => 0,
            'movies' => []
        ];
        
        if (isset($response['detail'])) {
            $currentPage = $response['detail']['current_page'] ?? 1;
            $lastPage = $response['detail']['last_page'] ?? 1;
            $formattedData['has_more'] = ($currentPage < $lastPage);
            $formattedData['total_items'] = $response['detail']['total'] ?? 0;
            
            if (isset($response['detail']['data']) && is_array($response['detail']['data'])) {
                foreach ($response['detail']['data'] as $item) {
                    $formattedData['movies'][] = [
                        'id' => $item['id'],
                        'type' => $item['type'],
                        'name' => $item['name'],
                        'title' => $item['title'] ?? $item['name'],
                        'image' => $item['image'],
                        'has_sub' => isset($item['is_sub']) ? ($item['is_sub'] == 1) : false,
                        'has_dubbed' => isset($item['is_dubbed']) ? ($item['is_dubbed'] == 1) : false
                    ];
                }
            }
        }
        return $formattedData;
    }
    
    public function getContentByGenre($type = 0, $genre_id = 400, $page = 1) {
        return $this->makeRequest('/home/main-page', ['type' => $type, 'action' => 1, 'genre_id' => $genre_id, 'page' => $page]);
    }
    
    public function getVitrinScroll($type = 0, $action = 0, $genre_id = -1, $page = 1, $limit = 3) {
        $response = $this->getMainPage($type, $action, $genre_id, $page);
        if (isset($response['error'])) return $response;
        
        $scrollData = [
            'success' => true, 'page' => (int)$page, 'limit' => (int)$limit, 'has_more' => false,
            'slider' => ($page == 1) ? ($response['slider'] ?? []) : [],
            'categories' => []
        ];
        
        if (isset($response['category']['data'])) {
            $categories = $response['category']['data'];
            $currentPage = $response['category']['currentPage'] ?? 1;
            $lastPage = $response['category']['lastPage'] ?? 1;
            
            if ($limit > 0 && count($categories) > $limit) {
                $categories = array_slice($categories, 0, $limit);
            }
            $scrollData['has_more'] = ($currentPage < $lastPage);
            
            foreach ($categories as $category) {
                $formattedCategory = ['id' => $category['id'], 'title' => $category['title'], 'items' => []];
                if (isset($category['detail']) && is_array($category['detail'])) {
                    foreach ($category['detail'] as $item) {
                        $formattedCategory['items'][] = [
                            'id' => $item['id'], 'type' => $item['type'], 'name' => $item['name'], 'title' => $item['title'],
                            'image' => $item['image'], 'has_sub' => $item['is_sub'] == 1, 'has_dubbed' => $item['is_dubbed'] == 1
                        ];
                    }
                }
                $scrollData['categories'][] = $formattedCategory;
            }
        }
        return $scrollData;
    }
    
    public function search($query, $type = 2, $page = 1) {
        return $this->makeRequest('/movie/search', ['type' => $type, 'page' => $page, 'value' => $query, 'priority' => '6', 'language_id' => 0]);
    }
    
    public function getDetails($id) {
        return $this->makeRequest('/movie/detail-info', ['id' => $id]);
    }
    
    public function getEpisodes($id, $season = 1, $reverse = 0, $page = 1) {
        return $this->makeRequest('/movie/episode-request', ['id' => $id, 'season' => $season, 'reverse' => $reverse, 'page' => $page]);
    }
    
    public function getLinkInfo($movieId, $movieLinkId, $movieQualityId, $isClone = 1, $type = 1) {
        return $this->makeRequest('/movie/link-info-request', ['movie_id' => $movieId, 'movie_link_id' => $movieLinkId, 'movie_quality_id' => $movieQualityId, 'is_clone' => $isClone, 'type' => $type]);
    }
    
    public function getStreamUrl($id, $qualityId, $season = -1, $episode = -1) {
        $details = $this->getDetails($id);
        if (isset($details['error'])) return $details;
        
        $isSeries = ($details['info']['type'] == 1);
        $qualityInfo = null; $movieLinkId = null; $movieQualityId = null; $isClone = 1;
        
        if ($isSeries && $season != -1 && $episode != -1) {
            $episodes = $this->getEpisodes($id, $season);
            if (isset($episodes['data'])) {
                foreach ($episodes['data'] as $ep) {
                    if ($ep['episode'] == $episode) {
                        foreach ($ep['quality'] as $q) {
                            if ($q['id'] == $qualityId) {
                                $qualityInfo = $q; $movieLinkId = $q['id']; $movieQualityId = $q['movie_quality_id'] ?? 2; $isClone = $q['download_clone'] ?? 1; break 2;
                            }
                        }
                    }
                }
            }
        } else {
            if (isset($details['link'][0]['episode'][0]['quality'])) {
                foreach ($details['link'][0]['episode'][0]['quality'] as $q) {
                    if ($q['id'] == $qualityId) {
                        $qualityInfo = $q; $movieLinkId = $q['id']; $movieQualityId = $q['movie_quality_id'] ?? 2; $isClone = $q['download_clone'] ?? 1; break;
                    }
                }
            }
        }
        
        if (!$qualityInfo) return ['error' => 'Quality not found'];
        
        $linkInfo = $this->getLinkInfo($id, $movieLinkId, $movieQualityId, $isClone, $isSeries ? 1 : 0);
        
        if (isset($linkInfo['link'])) {
            $link = $linkInfo['link'];
            return [
                'success' => true,
                'url' => $link['url'] ?? $link['url_clone'] ?? null,
                'srt' => $link['srt'] ?? $link['srt_clone'] ?? null,
                'vtt' => $link['srt_vtt'] ?? $link['srt_vtt_clone'] ?? null,
                'size' => $qualityInfo['size'] ?? null
            ];
        }
        return ['error' => 'Could not retrieve stream URL'];
    }
    
    public function getQualities($id, $season = -1, $episode = -1) {
        $details = $this->getDetails($id);
        if (isset($details['error'])) return $details;
        
        $isSeries = ($details['info']['type'] == 1);
        $qualities = [];
        
        if ($isSeries && $season != -1 && $episode != -1) {
            $episodes = $this->getEpisodes($id, $season);
            if (isset($episodes['data'])) {
                foreach ($episodes['data'] as $ep) {
                    if ($ep['episode'] == $episode) {
                        foreach ($ep['quality'] as $q) {
                            $qualities[] = ['id' => $q['id'], 'type' => $q['quality_type'], 'title' => $q['quality_title'], 'size' => $q['size']];
                        }
                        break;
                    }
                }
            }
        } else {
            if (isset($details['link'][0]['episode'][0]['quality'])) {
                foreach ($details['link'][0]['episode'][0]['quality'] as $q) {
                    $qualities[] = ['id' => $q['id'], 'type' => $q['quality_type'], 'title' => $q['quality_title'], 'size' => $q['size']];
                }
            }
        }
        
        return ['success' => true, 'content_id' => $id, 'qualities' => $qualities];
    }
}

$api = new MovielixAPI();
$action = isset($_GET['action']) ? $_GET['action'] : (isset($_POST['action']) ? $_POST['action'] : '');
$response = [];

try {
    switch ($action) {
        case 'main':
        case 'vitrin':
            $type = isset($_GET['type']) ? intval($_GET['type']) : 0;
            $action_param = isset($_GET['action_param']) ? intval($_GET['action_param']) : 0;
            $genre_id = isset($_GET['genre_id']) ? intval($_GET['genre_id']) : -1;
            $page = isset($_GET['page']) ? intval($_GET['page']) : 1;
            $limit = isset($_GET['limit']) ? intval($_GET['limit']) : 3;
            $response = $api->getVitrinScroll($type, $action_param, $genre_id, $page, $limit);
            break;
            
        case 'refresh_token':
            $api->refreshToken();
            $response = ['success' => true, 'message' => 'Token refreshed successfully'];
            break;
            
        case 'category_items':
            $id = isset($_GET['id']) ? intval($_GET['id']) : 0;
            $page = isset($_GET['page']) ? intval($_GET['page']) : 1;
            $response = $api->getCategoryContent($id, $page);
            break;
            
        case 'actor_details':
            $id = isset($_GET['id']) ? intval($_GET['id']) : (isset($_POST['id']) ? intval($_POST['id']) : 0);
            $page = isset($_GET['page']) ? intval($_GET['page']) : (isset($_POST['page']) ? intval($_POST['page']) : 1);
            $response = $api->getActorDetails($id, $page);
            break;
            
        case 'genres':
            $type = isset($_GET['type']) ? intval($_GET['type']) : 2;
            $response = ($type == 0) ? $api->getMovieGenres() : (($type == 1) ? $api->getSeriesGenres() : $api->getAllGenres());
            break;
            
        case 'by_genre':
            $type = isset($_GET['type']) ? intval($_GET['type']) : 0;
            $genre_id = isset($_GET['genre_id']) ? intval($_GET['genre_id']) : 0;
            $page = isset($_GET['page']) ? intval($_GET['page']) : 1;
            $result = $api->getContentByGenre($type, $genre_id, $page);
            
            if (isset($result['category']['data'])) {
                $formatted = ['success' => true, 'page' => $page, 'current_page' => $result['category']['currentPage'] ?? 1, 'last_page' => $result['category']['lastPage'] ?? 1, 'categories' => []];
                foreach ($result['category']['data'] as $category) {
                    $formattedCategory = ['id' => $category['id'], 'title' => $category['title'], 'items' => []];
                    if (isset($category['detail'])) {
                        foreach ($category['detail'] as $item) {
                            $formattedCategory['items'][] = ['id' => $item['id'], 'title' => $item['title'] ?? $item['name'], 'image' => $item['image'], 'has_sub' => $item['is_sub'] == 1, 'has_dubbed' => $item['is_dubbed'] == 1];
                        }
                    }
                    $formatted['categories'][] = $formattedCategory;
                }
                $response = $formatted;
            } else {
                $response = $result;
            }
            break;
            
        case 'search':
            $query = isset($_GET['q']) ? $_GET['q'] : '';
            $type = isset($_GET['type']) ? intval($_GET['type']) : 2;
            $page = isset($_GET['page']) ? intval($_GET['page']) : 1;
            $response = $api->search($query, $type, $page);
            break;
            
        case 'details':
            $id = isset($_GET['id']) ? intval($_GET['id']) : 0;
            $response = $api->getDetails($id);
            break;
            
        case 'episodes':
            $id = isset($_GET['id']) ? intval($_GET['id']) : 0;
            $season = isset($_GET['season']) ? intval($_GET['season']) : 1;
            $response = $api->getEpisodes($id, $season);
            break;
            
        case 'qualities':
            $id = isset($_GET['id']) ? intval($_GET['id']) : 0;
            $season = isset($_GET['season']) ? intval($_GET['season']) : -1;
            $episode = isset($_GET['episode']) ? intval($_GET['episode']) : -1;
            $response = $api->getQualities($id, $season, $episode);
            break;
            
        case 'stream':
            $id = isset($_GET['id']) ? intval($_GET['id']) : 0;
            $quality_id = isset($_GET['quality_id']) ? intval($_GET['quality_id']) : 0;
            $season = isset($_GET['season']) ? intval($_GET['season']) : -1;
            $episode = isset($_GET['episode']) ? intval($_GET['episode']) : -1;
            $response = $api->getStreamUrl($id, $quality_id, $season, $episode);
            break;
            
        case 'download':
            $id = isset($_GET['id']) ? intval($_GET['id']) : 0;
            $quality_id = isset($_GET['quality_id']) ? intval($_GET['quality_id']) : 0;
            $season = isset($_GET['season']) ? intval($_GET['season']) : -1;
            $episode = isset($_GET['episode']) ? intval($_GET['episode']) : -1;
            $streamData = $api->getStreamUrl($id, $quality_id, $season, $episode);
            if (isset($streamData['url'])) {
                header("Location: " . $streamData['url']);
                exit();
            }
            $response = ['error' => 'Download link not found'];
            break;
            
        default:
            $response = ['error' => 'Invalid action'];
    }
} catch (Exception $e) {
    $response = ['error' => 'Server error: ' . $e->getMessage()];
}

echo json_encode($response, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
?>