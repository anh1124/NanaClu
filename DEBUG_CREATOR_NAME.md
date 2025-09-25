# Debug Creator Name Issue - Logcat Guide

## Các Tag Log để theo dõi:

### 1. **EventRepository** - Theo dõi quá trình load events từ database
```
Tag: EventRepository
```

**Logs quan trọng:**
- `"Getting events for groupId: [groupId]"`
- `"Document: [eventId], data: [raw data]"`
- `"Added event: [title], status: [status], creatorId: [creatorId], creatorName: [creatorName]"`

### 2. **EventListFragment** - Theo dõi quá trình nhận events từ repository
```
Tag: EventListFragment
```

**Logs quan trọng:**
- `"Loaded [count] events for groupId: [groupId]"`
- `"Event: [title], status: [status], creatorId: [creatorId], creatorName: [creatorName]"`

### 3. **EventAdapter** - Theo dõi quá trình bind và load creator name
```
Tag: EventAdapter
```

**Logs quan trọng:**
- `"Binding event: [title], creatorId: [creatorId], creatorName: [creatorName]"`
- `"Using cached creator name: [name]"` - Nếu đã có creatorName
- `"Loading creator name for ID: [creatorId]"` - Nếu cần load từ Firestore
- `"Both creatorId and creatorName are null/empty for event: [title]"` - Nếu cả hai đều null
- `"Starting to load creator name for ID: [creatorId]"`
- `"Firestore response for creatorId [creatorId], exists: [true/false]"`
- `"Retrieved creator name: '[name]' for ID: [creatorId]"`
- `"Setting creator name: [name]"`
- `"Updating event object with creator name: [name]"`

## Cách sử dụng Logcat:

### 1. Mở Logcat trong Android Studio:
- View → Tool Windows → Logcat

### 2. Filter theo tag:
```
Tag: EventRepository
Tag: EventListFragment  
Tag: EventAdapter
```

### 3. Hoặc filter theo package:
```
Package: com.example.nanaclu
```

### 4. Hoặc sử dụng command line:
```bash
adb logcat -s EventRepository EventListFragment EventAdapter
```

## Các trường hợp cần chú ý:

### Trường hợp 1: creatorId và creatorName đều null
```
EventRepository: Added event: [title], status: active, creatorId: null, creatorName: null
EventAdapter: Both creatorId and creatorName are null/empty for event: [title]
```
**→ Event được tạo không có thông tin creator**

### Trường hợp 2: Có creatorId nhưng creatorName null
```
EventRepository: Added event: [title], status: active, creatorId: Zv3vOIWincXojZmW07ib6UEEUqE3, creatorName: null
EventAdapter: Loading creator name for ID: Zv3vOIWincXojZmW07ib6UEEUqE3
EventAdapter: Starting to load creator name for ID: Zv3vOIWincXojZmW07ib6UEEUqE3
```
**→ Cần load creatorName từ Firestore**

### Trường hợp 3: Firestore không tìm thấy user
```
EventAdapter: Firestore response for creatorId Zv3vOIWincXojZmW07ib6UEEUqE3, exists: false
EventAdapter: Creator document does not exist for ID: Zv3vOIWincXojZmW07ib6UEEUqE3
```
**→ User đã bị xóa hoặc creatorId sai**

### Trường hợp 4: User tồn tại nhưng không có field "name"
```
EventAdapter: Firestore response for creatorId Zv3vOIWincXojZmW07ib6UEEUqE3, exists: true
EventAdapter: Retrieved creator name: 'null' for ID: Zv3vOIWincXojZmW07ib6UEEUqE3
EventAdapter: Creator name is null/empty in document for ID: Zv3vOIWincXojZmW07ib6UEEUqE3
```
**→ User document thiếu field "name"**

## Các bước debug:

1. **Mở app và vào danh sách sự kiện**
2. **Mở Logcat và filter theo các tag trên**
3. **Xem log khi load events:**
   - EventRepository có load được creatorId không?
   - EventListFragment có nhận được creatorId không?
   - EventAdapter có được gọi không?
   - onBindViewHolder có được gọi không?
   - loadCreatorName có được gọi không?
4. **Kiểm tra kết quả load từ Firestore:**
   - User document có tồn tại không?
   - Field "name" có giá trị không?

## Vấn đề hiện tại từ log:

Từ log bạn cung cấp, tôi thấy:
- ✅ EventRepository load được creatorId: `Zv3vOIWincXojZmW07ib6UEEUqE3`
- ❌ Không thấy log từ EventListFragment
- ❌ Không thấy log từ EventAdapter

**Có thể có vấn đề ở:**
1. Callback từ Repository không được gọi
2. Fragment không nhận được dữ liệu
3. Adapter không được update
4. ViewHolder không được bind

## Ví dụ log hoàn chỉnh:

```
EventRepository: Getting events for groupId: 8ab93a7e-964d-4f29-bc22-335f331a9f18
EventRepository: Query successful, found 1 documents
EventRepository: Document: rlC9a1zI3f5v85scBqJw, data: {title=test sk, creatorId=Zv3vOIWincXojZmW07ib6UEEUqE3, ...}
EventRepository: Added event: test sk, status: active, creatorId: Zv3vOIWincXojZmW07ib6UEEUqE3, creatorName: null
EventListFragment: Loaded 1 events for groupId: 8ab93a7e-964d-4f29-bc22-335f331a9f18
EventListFragment: Event: test sk, status: active, creatorId: Zv3vOIWincXojZmW07ib6UEEUqE3, creatorName: null
EventAdapter: Binding event: test sk, creatorId: Zv3vOIWincXojZmW07ib6UEEUqE3, creatorName: null
EventAdapter: Loading creator name for ID: Zv3vOIWincXojZmW07ib6UEEUqE3
EventAdapter: Starting to load creator name for ID: Zv3vOIWincXojZmW07ib6UEEUqE3
EventAdapter: Firestore response for creatorId Zv3vOIWincXojZmW07ib6UEEUqE3, exists: true
EventAdapter: Retrieved creator name: 'John Doe' for ID: Zv3vOIWincXojZmW07ib6UEEUqE3
EventAdapter: Setting creator name: John Doe
EventAdapter: Updating event object with creator name: John Doe
```

**→ Kết quả: Hiển thị "Tạo bởi: John Doe"**
